package com.flowsight.ocr;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Infers the receipt currency from explicit codes and symbols, tax vocabulary (GSTIN/CGST,
 * VAT number, TRN, sales tax), phone prefixes and place names. Returns no currency when there
 * is no cue rather than assuming one.
 */
public final class CurrencyDetector {

    public enum Currency { INR, SAR, AED, USD, EUR, GBP }

    /** code is null when nothing on the receipt points to a currency. */
    public record Guess(String code, double confidence, String cue) {
        static final Guess UNKNOWN = new Guess(null, 0.0, null);
    }

    private record Cue(Currency currency, Pattern pattern, int weight, String name) {
    }

    private static final int CODE = 6;
    private static final int TAX_ID = 4;
    private static final int PLACE = 2;
    private static final int WEAK = 1;

    private static final List<Cue> CUES = List.of(
        cue(Currency.INR, "₹|\\bRs\\.?\\s?\\d|\\bINR\\b", CODE, "symbol"),
        cue(Currency.INR, "\\bGSTIN\\b|\\b[CSIU]GST\\b|\\bHSN\\b|\\bFSSAI\\b", TAX_ID, "gst"),
        cue(Currency.INR, "\\+91[\\s-]?\\d|\\b(?:Bengaluru|Bangalore|Mumbai|Delhi|Chennai|Kolkata|Hyderabad|Pune|Gurgaon|Gurugram|Noida)\\b", PLACE, "place"),
        cue(Currency.INR, "\\bUPI\\b|\\bPaytm\\b|\\bRuPay\\b", WEAK, "payment"),
        cue(Currency.SAR, "\\bSAR\\b|\\bSR\\s?\\d|ر\\.س|ريال", CODE, "symbol"),
        cue(Currency.SAR, "\\bVAT\\s*(?:No|Number|Reg)\\b[^\\n]*\\b3\\d{13}3\\b|\\bSimplified\\s+Tax\\s+Invoice\\b", TAX_ID, "vat-id"),
        cue(Currency.SAR, "\\+966[\\s-]?\\d|\\b(?:Riyadh|Jeddah|Khobar|Dammam|Makkah|Mecca|Madinah|Medina)\\b|\\bmada\\b", PLACE, "place"),
        cue(Currency.AED, "\\bAED\\b|\\bDhs?\\.?\\s?\\d|د\\.إ|درهم", CODE, "symbol"),
        cue(Currency.AED, "\\bTRN\\b", TAX_ID, "trn"),
        cue(Currency.AED, "\\+971[\\s-]?\\d|\\b(?:Dubai|Abu\\s+Dhabi|Sharjah|Ajman|Fujairah|UAE)\\b", PLACE, "place"),
        cue(Currency.USD, "\\bUSD\\b|US\\$", CODE, "symbol"),
        cue(Currency.USD, "\\$\\s?\\d", CODE - 2, "dollar"),
        cue(Currency.USD, "\\bSales\\s+Tax\\b", TAX_ID, "sales-tax"),
        // state code + ZIP is case-sensitive so "No 12345" does not count
        new Cue(Currency.USD, Pattern.compile("\\b[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?\\b|\\(\\d{3}\\)\\s?\\d{3}-\\d{4}"), PLACE, "place"),
        cue(Currency.EUR, "€|\\bEUR\\b", CODE, "symbol"),
        cue(Currency.GBP, "£|\\bGBP\\b", CODE, "symbol")
    );

    private CurrencyDetector() {
    }

    private static Cue cue(Currency c, String regex, int weight, String name) {
        return new Cue(c, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight, name);
    }

    public static Guess detect(String text) {
        if (text == null || text.isBlank()) return Guess.UNKNOWN;
        Map<Currency, Integer> scores = new EnumMap<>(Currency.class);
        Map<Currency, String> bestCue = new EnumMap<>(Currency.class);
        Map<Currency, Integer> bestWeight = new EnumMap<>(Currency.class);
        for (Cue cue : CUES) {
            if (!cue.pattern().matcher(text).find()) continue;
            scores.merge(cue.currency(), cue.weight(), Integer::sum);
            if (cue.weight() > bestWeight.getOrDefault(cue.currency(), 0)) {
                bestWeight.put(cue.currency(), cue.weight());
                bestCue.put(cue.currency(), cue.name());
            }
        }
        if (scores.isEmpty()) return Guess.UNKNOWN;

        Currency top = null;
        int topScore = 0;
        int second = 0;
        for (Map.Entry<Currency, Integer> e : scores.entrySet()) {
            if (e.getValue() > topScore) {
                second = topScore;
                topScore = e.getValue();
                top = e.getKey();
            } else if (e.getValue() > second) {
                second = e.getValue();
            }
        }
        if (topScore == second) return new Guess(null, 0.0, "conflicting cues");
        return new Guess(top.name(), confidence(topScore, second), bestCue.get(top));
    }

    // Evidence strength to a probability; the bands were checked against the synthetic eval set.
    private static double confidence(int top, int second) {
        int margin = top - second;
        if (top >= CODE + TAX_ID && margin >= CODE) return 0.97;
        if (margin >= CODE) return 0.93;
        if (margin >= TAX_ID) return 0.85;
        if (margin >= PLACE) return 0.70;
        return 0.50;
    }
}
