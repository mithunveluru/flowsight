package com.flowsight.ocr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one OCR pass into structured lines: every amount with its token-classified label,
 * the product lines above the first summary line, and the merchant, date and currency cues.
 * It does not decide the total; {@link ReceiptReconciler} does that across passes.
 */
@Component
@RequiredArgsConstructor
public class ReceiptTextParser {

    public record AmountLine(ReceiptLabels.Kind kind, String text, BigDecimal value, int index) {
    }

    public record Item(String name, Integer quantity, BigDecimal unitPrice, BigDecimal amount, int index) {
    }

    public record Parsed(
        List<String> lines,
        List<AmountLine> amounts,
        List<Item> items,
        MerchantCandidate merchant,
        CurrencyDetector.Guess currency,
        ReceiptDates.Guess date,
        double meanConfidence
    ) {
        public String text() {
            return String.join("\n", lines);
        }

        public List<AmountLine> ofKind(ReceiptLabels.Kind kind) {
            return amounts.stream().filter(a -> a.kind() == kind).toList();
        }
    }

    // integers are accepted as amounts only on these lines ("Total 254"), never on product lines
    private static final Set<ReceiptLabels.Kind> INTEGER_OK = Set.of(
        ReceiptLabels.Kind.GRAND_TOTAL, ReceiptLabels.Kind.TOTAL, ReceiptLabels.Kind.SUBTOTAL,
        ReceiptLabels.Kind.TENDERED, ReceiptLabels.Kind.CHANGE);
    private static final int MAX_INTEGER_AMOUNT_DIGITS = 6;
    private static final BigDecimal MAX_QTY = BigDecimal.valueOf(99);
    private static final Pattern LEADING_QTY = Pattern.compile("^(\\d{1,2})\\s+(?=\\p{L})");
    private static final Pattern X_QTY = Pattern.compile("(?i)(?:^|\\s)(?:x\\s?(\\d{1,3})|(\\d{1,3})\\s?x)(?=\\s|$)");
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*[\\p{L}][\\p{L} .#]{0,18}:\\s*\\S");

    private final MerchantExtractor merchantExtractor;

    public Parsed read(OcrDocument document) {
        List<String> lines = document.getLines().stream()
            .map(OcrLine::getText)
            .filter(t -> t != null && !t.isBlank())
            .map(String::trim)
            .toList();
        String text = String.join("\n", lines);
        CurrencyDetector.Guess currency = CurrencyDetector.detect(MoneyParser.normalizeDigits(text));
        ReceiptDates.Guess date = ReceiptDates.find(lines, currency.code());
        MerchantCandidate merchant = merchantExtractor.extractWithScore(document);

        List<AmountLine> amounts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            AmountLine a = amountLine(lines.get(i), i);
            if (a != null) amounts.add(a);
        }
        double meanConfidence = document.getLines().stream().mapToDouble(OcrLine::getConfidence).average().orElse(0.0);
        return new Parsed(lines, amounts, items(lines, amounts), merchant, currency, date, meanConfidence);
    }

    static AmountLine amountLine(String line, int index) {
        List<MoneyParser.Amount> found = MoneyParser.findAmounts(line);
        if (found.isEmpty()) return null;
        ReceiptLabels.Kind kind = ReceiptLabels.classify(line);
        String normalized = MoneyParser.normalize(line);
        MoneyParser.Amount chosen = null;
        for (MoneyParser.Amount a : found) {
            if (a.twoDecimals()) chosen = a;
        }
        if (chosen == null && INTEGER_OK.contains(kind)) {
            MoneyParser.Amount last = found.get(found.size() - 1);
            boolean masked = last.start() > 0 && "*#xX".indexOf(normalized.charAt(last.start() - 1)) >= 0;
            if (!masked && last.value().signum() > 0
                && last.value().toBigInteger().toString().length() <= MAX_INTEGER_AMOUNT_DIGITS) {
                chosen = last;
            }
        }
        if (chosen == null) return null;
        BigDecimal value = chosen.negative() ? chosen.value().negate() : chosen.value();
        return new AmountLine(kind, line, value, index);
    }

    // product lines: priced, unlabelled lines above the first summary or total line
    static List<Item> items(List<String> lines, List<AmountLine> amounts) {
        int end = amounts.stream()
            .filter(a -> a.kind().isSummary() || a.kind().isTotal())
            .mapToInt(AmountLine::index).min().orElse(lines.size());
        List<Item> items = new ArrayList<>();
        for (AmountLine a : amounts) {
            if (a.index() >= end || a.kind() != ReceiptLabels.Kind.OTHER || a.value().signum() <= 0) continue;
            if (KEY_VALUE.matcher(a.text()).find()) continue;
            Item item = item(a);
            if (item != null) items.add(item);
        }
        return items;
    }

    static Item item(AmountLine line) {
        String text = MoneyParser.normalize(line.text());
        List<MoneyParser.Amount> amounts = MoneyParser.findAmounts(text);
        int nameEnd = text.length();
        // the name ends where the trailing run of numbers (qty, rate, amount) begins
        for (int i = amounts.size() - 1; i >= 0; i--) {
            MoneyParser.Amount a = amounts.get(i);
            String between = text.substring(a.end(), nameEnd);
            if (!between.isBlank() && !between.trim().matches("[xX@*]?")) break;
            nameEnd = a.start();
        }
        Integer qty = null;
        Matcher x = X_QTY.matcher(text);
        if (x.find() && x.start() < nameEnd + 4) {
            qty = Integer.parseInt(x.group(1) != null ? x.group(1) : x.group(2));
            nameEnd = Math.min(nameEnd, x.start());
        }
        String name = text.substring(0, nameEnd).replaceAll("[\\s:.\\-@*]+$", "").trim();
        Matcher lead = LEADING_QTY.matcher(name);
        if (lead.find()) {
            qty = Integer.parseInt(lead.group(1));
            name = name.substring(lead.end()).trim();
        }
        long letters = name.chars().filter(Character::isLetter).count();
        if (letters < 2) return null;

        BigDecimal amount = line.value();
        BigDecimal unit = null;
        List<MoneyParser.Amount> decimals = amounts.stream().filter(MoneyParser.Amount::twoDecimals).toList();
        if (decimals.size() >= 2) {
            BigDecimal rate = decimals.get(decimals.size() - 2).value();
            if (rate.signum() > 0 && amount.remainder(rate).signum() == 0) {
                BigDecimal implied = amount.divide(rate, 0, RoundingMode.UNNECESSARY);
                if (implied.signum() > 0 && implied.compareTo(MAX_QTY) <= 0) {
                    unit = rate;
                    qty = implied.intValue();
                }
            }
        }
        return new Item(name, qty, unit, amount, line.index());
    }
}
