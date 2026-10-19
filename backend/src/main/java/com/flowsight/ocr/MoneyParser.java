package com.flowsight.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads printed money amounts in the formats receipts use: 1,234.50 (US/UK), 1.234,50 (EU),
 * 1,23,456.00 (Indian grouping) and Arabic-Indic or Eastern Arabic digits with the Arabic
 * decimal and thousands separators. The decimal separator is the last '.' or ',' when exactly
 * two digits follow it; every other separator is grouping.
 */
public final class MoneyParser {

    // digits joined by '.', ',' or an apostrophe; not part of a percentage
    private static final Pattern NUMBER = Pattern.compile(
        "(?<![\\d])(?<![\\d][.,])(\\d+(?:[.,']\\d+)*)(?!\\s*%)(?![.,]?\\d)");
    private static final int MAX_INTEGER_DIGITS = 9;
    // OCR spacing errors inside one amount: "1, 335.56" and "336 .66"
    private static final Pattern SPLIT_GROUP = Pattern.compile("(?<=\\d[.,]) (?=\\d{3}(?:[.,]\\d))");
    private static final Pattern SPLIT_DECIMAL = Pattern.compile("(?<=\\d) (?=[.,]\\d{2}(?!\\d))");

    private MoneyParser() {
    }

    /** One amount found in a line: its value, its position, and whether it had two decimals. */
    public record Amount(BigDecimal value, int start, int end, boolean twoDecimals, boolean negative) {
    }

    /** Arabic-Indic (U+0660) and Eastern Arabic (U+06F0) digits and separators to ASCII. */
    public static String normalizeDigits(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c == '٫') sb.append('.');
            else if (c == '٬') sb.append(',');
            else if (c == ' ' || c == ' ') sb.append(' ');
            else sb.append(c);
        }
        return sb.toString();
    }

    /** normalizeDigits plus repair of spaces OCR inserts inside an amount; findAmounts positions refer to this text. */
    public static String normalize(String text) {
        String s = normalizeDigits(text);
        s = SPLIT_GROUP.matcher(s).replaceAll("");
        return SPLIT_DECIMAL.matcher(s).replaceAll("");
    }

    /** Parses a single amount such as "1.234,50", "₹1,23,456.00", "(12.75)" or "١٢٣٫٥٠". */
    public static Optional<BigDecimal> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        List<Amount> amounts = findAmounts(raw);
        if (amounts.size() != 1) return Optional.empty();
        Amount a = amounts.get(0);
        return Optional.of(a.negative() ? a.value().negate() : a.value());
    }

    /** All amounts in a line, left to right. Percentages are skipped. */
    public static List<Amount> findAmounts(String line) {
        String text = normalize(line);
        List<Amount> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String token = m.group(1);
            if (gluedToWord(text, m.start(1), m.end(1))) continue;
            ParsedNumber number = interpret(token);
            if (number == null) continue;
            boolean negative = isNegative(text, m.start(1), m.end(1));
            out.add(new Amount(number.value, m.start(1), m.end(1), number.twoDecimals, negative));
        }
        return out;
    }

    // "qr4", "4,VO", "2L" or "55in" are misreads or sizes, not prices; "Rs.254.00" and "SAR45.00" are prices
    private static boolean gluedToWord(String text, int start, int end) {
        int i = start;
        while (i > 0 && (text.charAt(i - 1) == '.' && i - 1 > 0 && Character.isLetter(text.charAt(i - 2)))) i--;
        if (i > 0 && Character.isLetter(text.charAt(i - 1)) && !isCurrencyWord(wordBefore(text, i))) return true;
        int j = end;
        if (j < text.length() && (text.charAt(j) == '.' || text.charAt(j) == ',')) j++;
        return j < text.length() && Character.isLetter(text.charAt(j)) && !isCurrencyWord(wordAfter(text, j));
    }

    private static String wordBefore(String text, int end) {
        int i = end;
        while (i > 0 && Character.isLetter(text.charAt(i - 1))) i--;
        return text.substring(i, end);
    }

    private static String wordAfter(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isLetter(text.charAt(i))) i++;
        return text.substring(start, i);
    }

    private static boolean isCurrencyWord(String w) {
        return CURRENCY_WORDS.contains(w.toUpperCase(java.util.Locale.ROOT));
    }

    private static final java.util.Set<String> CURRENCY_WORDS = java.util.Set.of(
        "RS", "INR", "SAR", "SR", "AED", "DH", "DHS", "USD", "US", "EUR", "GBP");

    private record ParsedNumber(BigDecimal value, boolean twoDecimals) {
    }

    private static ParsedNumber interpret(String token) {
        int lastSep = Math.max(token.lastIndexOf('.'), Math.max(token.lastIndexOf(','), token.lastIndexOf('\'')));
        String intPart;
        String fraction = "";
        if (lastSep >= 0 && token.charAt(lastSep) != '\'' && token.length() - lastSep - 1 == 2) {
            intPart = token.substring(0, lastSep);
            fraction = token.substring(lastSep + 1);
        } else if (lastSep >= 0 && token.length() - lastSep - 1 != 3) {
            // a separator followed by 1 or 4+ digits is neither grouping nor a 2-place decimal
            if (token.chars().filter(c -> c == '.' || c == ',').count() != 1) return null;
            intPart = token.substring(0, lastSep);
            fraction = token.substring(lastSep + 1);
            if (fraction.length() > 3) return null;
        } else {
            intPart = token;
        }
        String digits = intPart.replaceAll("[.,']", "");
        if (digits.isEmpty() || digits.length() > MAX_INTEGER_DIGITS) return null;
        BigDecimal value = new BigDecimal(fraction.isEmpty() ? digits : digits + "." + fraction);
        return new ParsedNumber(value.setScale(2, RoundingMode.HALF_UP), fraction.length() == 2);
    }

    private static boolean isNegative(String text, int start, int end) {
        String before = text.substring(Math.max(0, start - 10), start);
        if (NEGATIVE_PREFIX.matcher(before).find()) return true;
        return end < text.length() && (text.charAt(end) == '-' || text.charAt(end) == ')')
            && (end + 1 >= text.length() || !Character.isDigit(text.charAt(end + 1)));
    }

    // a minus sign or opening parenthesis, optionally followed by a currency marker, e.g. "-Rs. 12.00"
    private static final Pattern NEGATIVE_PREFIX = Pattern.compile(
        "(?:^|[\\s:])[-−(]\\s*(?:Rs\\.?|INR|SAR|AED|USD|EUR|GBP|SR|[$₹€£])?\\s*$",
        Pattern.CASE_INSENSITIVE);
}
