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

/**
 * Parses CSV bank exports into a normalized {@link CsvRow} structure using
 * header-driven column mapping rather than hardcoded bank formats.
 *
 * <p>Two ingestion modes, both routed by {@link StatementFormatDetector}:
 * <ul>
 *   <li>{@link StatementFormatDetector.Format#EXPLICIT_AMOUNT} -
 *       resolves amount + direction from whatever combination of
 *       amount / debit / credit / type columns the file provides. Handles
 *       signed values (e.g. Debit column containing {@code -1500.0}) and the
 *       traditional unsigned-magnitude convention without conflating them.</li>
 *   <li>{@link StatementFormatDetector.Format#BALANCE_ONLY} -
 *       reconstructs amounts from running-balance deltas via
 *       {@link BalanceDeltaCalculator}.</li>
 * </ul>
 *
 * <p>A row that cannot have an amount resolved is reported in
 * {@link ParseResult#getErrors()} - never silently coerced to zero.
 */
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

    // -------------------------------------------------------------------------
    // Explicit-amount path - drives amount/debit/credit/type from the
    // DetectionResult column mapping. No bank-specific branches.
    // -------------------------------------------------------------------------
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

    /**
     * Centralized amount + direction resolver. Encodes the rules described
     * on the class doc. Throws {@link IllegalArgumentException} when no
     * amount can be resolved (never returns a silent zero).
     */
    private Resolution resolveAmount(BigDecimal amount,
                                     BigDecimal debit,
                                     BigDecimal credit,
                                     String typeStr) {
        // Rule 1: two-column debit/credit layout.
        // A non-null, non-zero entry in either column wins; sign of the *column*
        // (not the cell value) determines direction. Cell value is abs()'d so
        // banks that export debits as negative or as positive both work.
        boolean debitFilled  = debit  != null && debit.signum()  != 0;
        boolean creditFilled = credit != null && credit.signum() != 0;
        if (debitFilled && creditFilled) {
            // Defensive: real bank exports never fill both. Pick the larger
            // absolute value, log a warning so we don't pretend it's clean.
            log.warn("Row has both debit ({}) and credit ({}) populated; using larger magnitude", debit, credit);
            BigDecimal d = debit.abs();
            BigDecimal c = credit.abs();
            return d.compareTo(c) >= 0
                ? new Resolution(d, true)
                : new Resolution(c, false);
        }
        if (debitFilled)  return new Resolution(debit.abs(),  true);
        if (creditFilled) return new Resolution(credit.abs(), false);

        // Rule 2: single amount column.
        if (amount != null) {
            // Explicit type column wins over sign.
            if (typeStr != null) {
                return new Resolution(amount.abs(), parseDirection(typeStr, /* default */ true));
            }
            // No type column - sign of the cell determines direction.
            // Convention: negative = debit, positive = credit.
            return new Resolution(amount.abs(), amount.signum() < 0);
        }

        throw new IllegalArgumentException(
            "No amount, debit, or credit value found - row produces zero, refusing to import");
    }

    /** Returns true when the string indicates a debit. Defaults to {@code defaultDebit} when ambiguous. */
    private boolean parseDirection(String typeStr, boolean defaultDebit) {
        String t = typeStr.trim().toLowerCase(Locale.ROOT);
        if (t.equals("debit") || t.equals("dr") || t.equals("d") || t.equals("withdrawal")
            || t.equals("expense") || t.equals("out")) return true;
        if (t.equals("credit") || t.equals("cr") || t.equals("c") || t.equals("deposit")
            || t.equals("income") || t.equals("in")) return false;
        return defaultDebit;
    }

    /**
     * Like {@link #parseMoney} but throws on non-blank, non-parseable values
     * so the row is rejected with a clear error instead of silently dropping
     * to zero.
     */
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

    // -------------------------------------------------------------------------
    // Balance-only path - delegates to BalanceDeltaCalculator. Unchanged.
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private String get(CSVRecord r, String header) {
        if (header == null) return null;
        try {
            String val = r.get(header);
            return (val == null || val.isBlank()) ? null : val.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Case-insensitive header lookup - used for optional columns not surfaced by the detector. */
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

    /**
     * Lenient money parser - returns {@code null} on blank/unparseable input.
     * Used by the balance-only path where each cell is validated separately
     * before reaching this helper.
     */
    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        String cleaned = raw.replaceAll("[₹$€£,\\s]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Internal carrier for the resolved (amount, direction) pair. */
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
