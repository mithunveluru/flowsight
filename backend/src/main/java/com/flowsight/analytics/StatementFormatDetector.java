package com.flowsight.analytics;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

// Decides from the CSV header whether the file has explicit amounts or only a running balance.
@Service
public class StatementFormatDetector {

    private static final Set<String> DATE_HEADERS = Set.of(
        "date", "txn date", "transaction date", "value date", "value dt", "posting date", "trans date"
    );
    private static final Set<String> DESC_HEADERS = Set.of(
        "description", "narration", "details", "particulars", "remarks", "memo", "transaction details"
    );
    private static final Set<String> AMOUNT_HEADERS = Set.of(
        "amount", "amt", "transaction amount", "txn amount", "transaction amt"
    );
    private static final Set<String> DEBIT_HEADERS = Set.of(
        "debit", "debit amount", "withdrawal", "withdrawal amount", "withdrawal amt",
        "withdrawals", "dr", "dr amount", "money out", "paid out"
    );
    private static final Set<String> CREDIT_HEADERS = Set.of(
        "credit", "credit amount", "deposit", "deposit amount", "deposit amt",
        "deposits", "cr", "cr amount", "money in", "paid in"
    );
    private static final Set<String> BALANCE_HEADERS = Set.of(
        "balance", "running balance", "closing balance", "available balance", "ledger balance"
    );
    private static final Set<String> TYPE_HEADERS = Set.of(
        "type", "txn type", "transaction type", "cr/dr", "dr/cr", "credit/debit", "debit/credit"
    );

    public DetectionResult detect(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return DetectionResult.builder().format(Format.UNKNOWN).build();
        }

        String dateCol    = matchAny(headers, DATE_HEADERS);
        String descCol    = matchAny(headers, DESC_HEADERS);
        String amountCol  = matchAny(headers, AMOUNT_HEADERS);
        String debitCol   = matchAny(headers, DEBIT_HEADERS);
        String creditCol  = matchAny(headers, CREDIT_HEADERS);
        String balanceCol = matchAny(headers, BALANCE_HEADERS);
        String typeCol    = matchAny(headers, TYPE_HEADERS);

        boolean hasAmount = amountCol != null || debitCol != null || creditCol != null;
        boolean hasBalance = balanceCol != null;
        boolean hasCore = dateCol != null && descCol != null;

        Format format;
        if (hasAmount) {
            format = Format.EXPLICIT_AMOUNT;
        } else if (hasCore && hasBalance) {
            format = Format.BALANCE_ONLY;
        } else {
            format = Format.UNKNOWN;
        }

        return DetectionResult.builder()
            .format(format)
            .dateColumn(dateCol)
            .descriptionColumn(descCol)
            .amountColumn(amountCol)
            .debitColumn(debitCol)
            .creditColumn(creditCol)
            .balanceColumn(balanceCol)
            .typeColumn(typeCol)
            .build();
    }

    private String matchAny(List<String> headers, Set<String> wanted) {
        for (String h : headers) {
            if (h == null) continue;
            String norm = h.trim().toLowerCase(Locale.ROOT);
            if (wanted.contains(norm)) return h;
        }
        return null;
    }

    public enum Format { EXPLICIT_AMOUNT, BALANCE_ONLY, UNKNOWN }

    @Value
    @Builder
    public static class DetectionResult {
        Format format;
        String dateColumn;
        String descriptionColumn;
        String amountColumn;
        String debitColumn;
        String creditColumn;
        String balanceColumn;
        String typeColumn;
    }
}
