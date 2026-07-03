package com.flowsight.analytics;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Parses CSV bank exports via header-driven column mapping (no per-bank branches).
// Rows whose amount can't be resolved are reported as errors, never coerced to zero.
@Service
@Slf4j
@RequiredArgsConstructor
public class CsvParserService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy")
    );

    private final StatementFormatDetector statementFormatDetector;
    private final BalanceDeltaCalculator balanceDeltaCalculator;
    private final ReconstructedTransactionMapper reconstructedTransactionMapper;

    public ParseResult parse(MultipartFile file) throws IOException {
        List<CsvRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser parser = CSVFormat.DEFAULT
                 .builder()
                 .setHeader()
                 .setSkipHeaderRecord(true)
                 .setTrim(true)
                 .setIgnoreEmptyLines(true)
                 .build()
                 .parse(reader)) {

            StatementFormatDetector.DetectionResult detection =
                statementFormatDetector.detect(parser.getHeaderNames());

            switch (detection.getFormat()) {
                case BALANCE_ONLY    -> parseBalanceOnly(parser, detection, rows, errors);
                case EXPLICIT_AMOUNT -> parseExplicitAmount(parser, detection, rows, errors);
                case UNKNOWN         -> errors.add(
                    "Unrecognized CSV header layout: " + parser.getHeaderNames()
                    + ". Expected one of date+description+amount, date+description+debit+credit, "
                    + "or date+description+balance.");
            }
        }

        return new ParseResult(rows, errors);
    }

    private void parseExplicitAmount(CSVParser parser,
                                     StatementFormatDetector.DetectionResult detection,
                                     List<CsvRow> rows,
                                     List<String> errors) {
        int rowNumber = 1;
        for (CSVRecord record : parser) {
            rowNumber++;
            try {
                CsvRow row = parseOneExplicitAmount(record, detection);
                rows.add(row);
            } catch (Exception e) {
                String msg = "Row " + rowNumber + ": " + e.getMessage();
                errors.add(msg);
                log.warn("CSV import skipped {}", msg);
            }
        }
    }

    private CsvRow parseOneExplicitAmount(CSVRecord record,
                                          StatementFormatDetector.DetectionResult d) {
        String dateStr = get(record, d.getDateColumn());
        if (dateStr == null) throw new IllegalArgumentException("Date is empty");
        LocalDate date = parseDate(dateStr);

        String description = get(record, d.getDescriptionColumn());
        if (description == null) {
            throw new IllegalArgumentException("Required columns (date, description) missing or empty");
        }

        BigDecimal amountRaw = parseMoneyStrict(get(record, d.getAmountColumn()), "amount");
        BigDecimal debitRaw  = parseMoneyStrict(get(record, d.getDebitColumn()),  "debit");
        BigDecimal creditRaw = parseMoneyStrict(get(record, d.getCreditColumn()), "credit");
        String typeStr       = get(record, d.getTypeColumn());

        Resolution r = resolveAmount(amountRaw, debitRaw, creditRaw, typeStr);

        String merchant = get(record, findHeader(record, "merchant"));

        return CsvRow.builder()
            .date(date)
            .description(description)
            .amount(r.amount)
            .isDebit(r.isDebit)
            .merchant(merchant)
            .rawText(record.toString())
            .build();
    }

    // resolve amount + direction; throws rather than returning a silent zero
    private Resolution resolveAmount(BigDecimal amount,
                                     BigDecimal debit,
                                     BigDecimal credit,
                                     String typeStr) {
        // debit/credit columns: the column decides direction, the cell is abs()'d
        // so banks exporting debits as negative or positive both work
        boolean debitFilled  = debit  != null && debit.signum()  != 0;
        boolean creditFilled = credit != null && credit.signum() != 0;
        if (debitFilled && creditFilled) {
            // exports never fill both; pick the larger magnitude and warn
            log.warn("Row has both debit ({}) and credit ({}) populated; using larger magnitude", debit, credit);
            BigDecimal d = debit.abs();
            BigDecimal c = credit.abs();
            return d.compareTo(c) >= 0
                ? new Resolution(d, true)
                : new Resolution(c, false);
        }
        if (debitFilled)  return new Resolution(debit.abs(),  true);
        if (creditFilled) return new Resolution(credit.abs(), false);

        if (amount != null) {
            // explicit type column wins over sign
            if (typeStr != null) {
                return new Resolution(amount.abs(), parseDirection(typeStr, true));
            }
            // no type column: negative sign = debit
            return new Resolution(amount.abs(), amount.signum() < 0);
        }

        throw new IllegalArgumentException(
            "No amount, debit, or credit value found - row produces zero, refusing to import");
    }

    // true = debit; defaults to defaultDebit when ambiguous
    private boolean parseDirection(String typeStr, boolean defaultDebit) {
        String t = typeStr.trim().toLowerCase(Locale.ROOT);
        if (t.equals("debit") || t.equals("dr") || t.equals("d") || t.equals("withdrawal")
            || t.equals("expense") || t.equals("out")) return true;
        if (t.equals("credit") || t.equals("cr") || t.equals("c") || t.equals("deposit")
            || t.equals("income") || t.equals("in")) return false;
        return defaultDebit;
    }

    // strict: throws on unparseable so the row is rejected, not silently zeroed
    private BigDecimal parseMoneyStrict(String raw, String columnName) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        String cleaned = raw.replaceAll("[₹$€£,\\s]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "invalid " + columnName + " value '" + raw + "'");
        }
    }

    private void parseBalanceOnly(CSVParser parser,
                                  StatementFormatDetector.DetectionResult detection,
                                  List<CsvRow> rows,
                                  List<String> errors) {
        List<BalanceDeltaCalculator.BalanceRow> buffer = new ArrayList<>();
        int rowNumber = 1;
        for (CSVRecord record : parser) {
            rowNumber++;
            String rawDate = get(record, detection.getDateColumn());
            String desc    = get(record, detection.getDescriptionColumn());
            String balStr  = get(record, detection.getBalanceColumn());

            if (rawDate == null || balStr == null) {
                errors.add("Row " + rowNumber + ": missing date or balance");
                continue;
            }

            LocalDate date;
            BigDecimal balance;
            try {
                date = parseDate(rawDate);
            } catch (Exception e) {
                errors.add("Row " + rowNumber + ": " + e.getMessage());
                continue;
            }
            balance = parseMoney(balStr);
            if (balance == null) {
                errors.add("Row " + rowNumber + ": invalid balance value '" + balStr + "'");
                continue;
            }

            buffer.add(BalanceDeltaCalculator.BalanceRow.builder()
                .date(date)
                .description(desc == null ? "" : desc)
                .balance(balance)
                .sourceRowNumber(rowNumber)
                .rawText(record.toString())
                .build());
        }

        BalanceDeltaCalculator.Result result = balanceDeltaCalculator.reconstruct(buffer);
        for (BalanceDeltaCalculator.ReconstructedTransaction tx : result.getTransactions()) {
            rows.add(reconstructedTransactionMapper.toCsvRow(tx));
        }
        errors.addAll(result.getWarnings());
    }

    private String get(CSVRecord r, String header) {
        if (header == null) return null;
        try {
            String val = r.get(header);
            return (val == null || val.isBlank()) ? null : val.trim();
        } catch (Exception e) {
            return null;
        }
    }

    // case-insensitive lookup for optional columns not surfaced by the detector
    private String findHeader(CSVRecord r, String name) {
        return r.getParser().getHeaderNames().stream()
            .filter(h -> h != null && h.equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Date is empty");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Unrecognized date format: " + raw);
    }

    // lenient: null on blank/unparseable
    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        String cleaned = raw.replaceAll("[₹$€£,\\s]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Resolution(BigDecimal amount, boolean isDebit) {}

    @Value
    @Builder
    public static class CsvRow {
        LocalDate date;
        String description;
        BigDecimal amount;
        boolean isDebit;
        String merchant;
        String rawText;
    }

    @Value
    public static class ParseResult {
        List<CsvRow> rows;
        List<String> errors;
    }
}
