package com.flowsight.analytics;

import lombok.Builder;
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
import java.util.Arrays;
import java.util.List;

/**
 * Parses CSV bank exports into a normalized CsvRow structure.
 *
 * Supports three formats detected from the header row:
 *   HDFC  – Date, Narration, Value Dt, Debit Amount, Credit Amount, Chq./Ref.No., Closing Balance
 *   SBI   – Txn Date, Value Date, Description, Ref No./Cheque No., Debit, Credit, Balance
 *   NATIVE – date, description, amount, type [, merchant, category, notes]
 *
 * Falls back to NATIVE if headers are unrecognized.
 */
@Service
@Slf4j
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

            CsvFormat format = detectFormat(parser.getHeaderNames());
            int rowNumber = 1;

            for (CSVRecord record : parser) {
                rowNumber++;
                try {
                    CsvRow row = parseRecord(record, format);
                    if (row != null) rows.add(row);
                } catch (Exception e) {
                    errors.add("Row " + rowNumber + ": " + e.getMessage());
                    log.debug("CSV parse error at row {}: {}", rowNumber, e.getMessage());
                }
            }
        }

        return new ParseResult(rows, errors);
    }

    private CsvFormat detectFormat(List<String> headers) {
        String normalized = String.join(",", headers).toLowerCase();
        if (normalized.contains("narration") && normalized.contains("debit amount")) {
            return CsvFormat.HDFC;
        } else if (normalized.contains("txn date") && normalized.contains("debit")) {
            return CsvFormat.SBI;
        }
        return CsvFormat.NATIVE;
    }

    private CsvRow parseRecord(CSVRecord record, CsvFormat format) {
        return switch (format) {
            case HDFC -> parseHdfc(record);
            case SBI  -> parseSbi(record);
            default   -> parseNative(record);
        };
    }

    private CsvRow parseHdfc(CSVRecord r) {
        String rawDate = get(r, "Date");
        String narration = get(r, "Narration");
        String debitStr  = get(r, "Debit Amount");
        String creditStr = get(r, "Credit Amount");

        LocalDate date = parseDate(rawDate);
        BigDecimal debit  = parseMoney(debitStr);
        BigDecimal credit = parseMoney(creditStr);

        boolean isDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal amount = isDebit ? debit : (credit != null ? credit : BigDecimal.ZERO);

        return CsvRow.builder()
            .date(date)
            .description(narration)
            .amount(amount)
            .isDebit(isDebit)
            .rawText(r.toString())
            .build();
    }

    private CsvRow parseSbi(CSVRecord r) {
        String rawDate   = get(r, "Txn Date");
        String desc      = get(r, "Description");
        String debitStr  = get(r, "Debit");
        String creditStr = get(r, "Credit");

        LocalDate date = parseDate(rawDate);
        BigDecimal debit  = parseMoney(debitStr);
        BigDecimal credit = parseMoney(creditStr);

        boolean isDebit = debit != null && debit.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal amount = isDebit ? debit : (credit != null ? credit : BigDecimal.ZERO);

        return CsvRow.builder()
            .date(date)
            .description(desc)
            .amount(amount)
            .isDebit(isDebit)
            .rawText(r.toString())
            .build();
    }

    private CsvRow parseNative(CSVRecord r) {
        // Expected: date, description, amount, type [, merchant, category, notes]
        List<String> headers = r.getParser().getHeaderNames();
        String dateStr  = safeGet(r, headers, "date");
        String desc     = safeGet(r, headers, "description");
        String amtStr   = safeGet(r, headers, "amount");
        String typeStr  = safeGet(r, headers, "type");
        String merchant = safeGet(r, headers, "merchant");

        if (dateStr == null || desc == null || amtStr == null) {
            throw new IllegalArgumentException("Required columns (date, description, amount) missing or empty");
        }

        LocalDate date   = parseDate(dateStr);
        BigDecimal amount = parseMoney(amtStr);
        if (amount == null) throw new IllegalArgumentException("Invalid amount: " + amtStr);

        boolean isDebit = typeStr == null
            || typeStr.equalsIgnoreCase("DEBIT")
            || typeStr.equalsIgnoreCase("DR")
            || typeStr.equalsIgnoreCase("D");

        return CsvRow.builder()
            .date(date)
            .description(desc)
            .amount(amount.abs())
            .isDebit(isDebit)
            .merchant(merchant)
            .rawText(r.toString())
            .build();
    }

    private String get(CSVRecord r, String header) {
        try {
            String val = r.get(header);
            return (val == null || val.isBlank()) ? null : val.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeGet(CSVRecord r, List<String> headers, String name) {
        return headers.stream()
            .filter(h -> h.equalsIgnoreCase(name))
            .findFirst()
            .map(h -> get(r, h))
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

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("-")) return null;
        // Remove currency symbols, commas, spaces
        String cleaned = raw.replaceAll("[₹$€£,\\s]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum CsvFormat { HDFC, SBI, NATIVE }

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
