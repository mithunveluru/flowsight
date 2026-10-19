package com.flowsight.ocr;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies the label of a receipt line by whole tokens, never by substring, so "Total Items",
 * "Total Tendered" and "Cash" are not taken for the total and "Subtotal" is not "Total".
 */
public final class ReceiptLabels {

    public enum Kind {
        GRAND_TOTAL, TOTAL, SUBTOTAL, TAX, TAX_TOTAL, DISCOUNT, SERVICE_CHARGE, TIP, ROUND_OFF,
        TENDERED, CHANGE, PAYMENT, ITEM_COUNT, SAVINGS, OTHER;

        public boolean isTotal() {
            return this == GRAND_TOTAL || this == TOTAL;
        }

        public boolean isSummary() {
            return this != OTHER && this != PAYMENT && this != TENDERED && this != CHANGE;
        }
    }

    private static final Set<String> TAX_WORDS = Set.of(
        "tax", "vat", "gst", "cgst", "sgst", "igst", "utgst", "cess", "hst", "pst", "qst", "taxes",
        "\u0636\u0631\u064A\u0628\u0629", "\u0627\u0644\u0636\u0631\u064A\u0628\u0629");
    // الإجمالي, الاجمالي, المجموع
    private static final Set<String> ARABIC_TOTAL_WORDS = Set.of(
        "\u0627\u0644\u0625\u062C\u0645\u0627\u0644\u064A", "\u0627\u0644\u0627\u062C\u0645\u0627\u0644\u064A",
        "\u0627\u0644\u0645\u062C\u0645\u0648\u0639");
    private static final Set<String> PAYMENT_WORDS = Set.of(
        "visa", "mastercard", "master", "amex", "rupay", "mada", "card", "upi", "debit", "credit", "paid",
        "gpay", "paytm", "phonepe", "wallet", "applepay", "knet", "netbanking", "eftpos");
    private static final Set<String> GRAND_PHRASES = Set.of(
        "grand total", "net payable", "amount payable", "total payable", "total due", "amount due",
        "balance due", "final total", "total amount due", "bill amount", "total incl vat",
        "total including vat", "total inc vat", "total incl tax", "amount to pay", "to pay", "net amount payable");
    private static final Set<String> SUBTOTAL_PHRASES = Set.of(
        "subtotal", "sub total", "sub tot", "subtot", "total excl vat", "total excluding vat", "total excl tax",
        "total before tax", "taxable amount", "taxable value", "amount excl vat", "total ex vat", "net sales",
        "\u0627\u0644\u0645\u062C\u0645\u0648\u0639 \u0627\u0644\u0641\u0631\u0639\u064A");

    private ReceiptLabels() {
    }

    /** Lower-case letter tokens of a label; digits and punctuation split tokens. */
    public static List<String> tokens(String label) {
        if (label == null) return List.of();
        String cleaned = label.toLowerCase(Locale.ROOT).replace("sub-total", "subtotal").replace("e-wallet", "wallet");
        return Arrays.stream(cleaned.split("[^\\p{L}]+")).filter(t -> !t.isEmpty()).toList();
    }

    public static Kind classify(String label) {
        List<String> t = tokens(label);
        if (t.isEmpty()) return Kind.OTHER;
        String joined = String.join(" ", t);

        if (has(t, "items", "item", "qty", "quantity", "pcs", "articles") && !has(t, "amount", "price", "value")
            && (has(t, "total", "no", "count", "number", "of") || t.size() == 1)) {
            return Kind.ITEM_COUNT;
        }
        if (has(t, "tendered", "tender", "received")) return Kind.TENDERED;
        if (has(t, "change") && !has(t, "exchange")) return Kind.CHANGE;
        if (has(t, "saved", "savings", "saving")) return Kind.SAVINGS;
        if (containsPhrase(joined, "round off", "roundoff", "rounding", "round", "rnd off", "rounded")) return Kind.ROUND_OFF;
        if (has(t, "tip", "tips", "gratuity")) return Kind.TIP;
        if (containsPhrase(joined, SUBTOTAL_PHRASES)) return Kind.SUBTOTAL;
        if (containsPhrase(joined, GRAND_PHRASES)) return Kind.GRAND_TOTAL;
        if (has(t, "total", "tot") && t.stream().anyMatch(TAX_WORDS::contains)) return Kind.TAX_TOTAL;
        if (has(t, "service") || containsPhrase(joined, "svc chg", "svc charge")) return Kind.SERVICE_CHARGE;
        if (has(t, "discount", "disc", "coupon", "promo", "offer", "less", "voucher")) return Kind.DISCOUNT;
        if (t.stream().anyMatch(TAX_WORDS::contains)) return Kind.TAX;
        if (has(t, "total", "tot", "payable") || containsPhrase(joined, "net amount")
            || t.stream().anyMatch(ARABIC_TOTAL_WORDS::contains)) {
            return Kind.TOTAL;
        }
        if (has(t, "cash")) return Kind.TENDERED;
        if (t.stream().anyMatch(PAYMENT_WORDS::contains)) return Kind.PAYMENT;
        return Kind.OTHER;
    }

    private static boolean has(List<String> tokens, String... words) {
        for (String w : words) {
            if (tokens.contains(w)) return true;
        }
        return false;
    }

    private static boolean containsPhrase(String joined, String... phrases) {
        return containsPhrase(joined, Set.of(phrases));
    }

    private static boolean containsPhrase(String joined, Set<String> phrases) {
        String padded = " " + joined + " ";
        for (String p : phrases) {
            if (padded.contains(" " + p + " ")) return true;
        }
        return false;
    }
}
