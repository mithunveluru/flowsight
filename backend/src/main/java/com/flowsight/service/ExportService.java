package com.flowsight.service;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// Downloadable exports; RFC 4180 CSV.
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    // keep in sync with the row format below
    private static final String CSV_HEADER =
        "Date,Description,Merchant,Category,Type,Amount,Currency,Source,Reviewed,Notes";

    private final TransactionRepository transactionRepository;

    // transactions CSV filtered by date range + optional category (RFC 4180)
    public String exportTransactionsCsv(
        UUID userId,
        LocalDate from,
        LocalDate to,
        TransactionCategory category
    ) {
        List<Transaction> txns = transactionRepository.findForExport(userId, category, from, to);

        StringBuilder sb = new StringBuilder(txns.size() * 120 + CSV_HEADER.length() + 2);
        sb.append(CSV_HEADER).append("\r\n");

        for (Transaction tx : txns) {
            appendCsvRow(sb,
                tx.getTransactionDate().format(ISO_DATE),
                tx.getDescription(),
                tx.getMerchant(),
                tx.getCategory() != null ? tx.getCategory().name() : "",
                tx.getType() != null ? tx.getType().name() : "",
                tx.getAmount() != null ? tx.getAmount().toPlainString() : "",
                tx.getCurrency() != null ? tx.getCurrency() : "INR",
                tx.getSource() != null ? tx.getSource().name() : "",
                String.valueOf(tx.isReviewed()),
                tx.getNotes()
            );
        }

        log.debug("CSV export: {} transactions for user {} ({} to {})",
            txns.size(), userId, from, to);
        return sb.toString();
    }

    public String csvFilename(LocalDate from, LocalDate to) {
        return String.format("flowsight-transactions-%s-%s.csv",
            from.format(ISO_DATE), to.format(ISO_DATE));
    }

    private static void appendCsvRow(StringBuilder sb, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields[i]));
        }
        sb.append("\r\n");
    }

    private static String escape(String field) {
        if (field == null) return "";
        boolean needsQuoting = field.indexOf(',') >= 0
            || field.indexOf('"') >= 0
            || field.indexOf('\n') >= 0
            || field.indexOf('\r') >= 0;
        if (!needsQuoting) return field;
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
