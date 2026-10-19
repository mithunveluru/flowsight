package com.flowsight.ocr;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks personal and business identifiers in receipt text before it leaves the backend:
 * emails, phone numbers, card numbers (including masked ****1234 tails), Indian GSTIN and PAN,
 * Gulf VAT/TRN registration numbers and other long digit runs. Prices and dates survive.
 */
public final class PiiRedactor {

    static final String MASK = "[redacted]";

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"),
        Pattern.compile("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z\\d]Z[A-Z\\d]\\b"),
        Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b"),
        Pattern.compile("[*xX#]{2,}\\s?\\d{2,4}\\b"),
        Pattern.compile("(?<![\\d.,])(?:\\d[ -]?){13,19}(?![\\d.,])"),
        Pattern.compile("\\+\\d{1,3}[\\s-]?\\(?\\d{1,4}\\)?(?:[\\s-]?\\d{2,4}){2,4}"),
        Pattern.compile("\\(\\d{3}\\)\\s?\\d{3}[\\s-]\\d{4}"),
        Pattern.compile("(?<![\\d.,])\\d{3}[\\s-]\\d{3}[\\s-]\\d{4}(?![\\d.,])"),
        Pattern.compile("(?<![\\d.,])\\d{9,}(?![\\d.,])")
    );

    private PiiRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = MoneyParser.normalizeDigits(text);
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll(MASK);
        }
        return out;
    }
}
