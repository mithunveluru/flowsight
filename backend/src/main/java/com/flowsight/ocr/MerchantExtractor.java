package com.flowsight.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Heuristic merchant-name extractor for OCR receipts: scores each line (position,
// OCR confidence, caps, length, letter density), hard-excludes noise, normalizes via aliases.
@Component
@Slf4j
public class MerchantExtractor {

    private static final Set<String> EXCLUDED_SUBSTRINGS = Set.of(
        // greeting / header noise
        "welcome to", "welcome", "thank you for shopping",
        // financial / receipt metadata
        "total", "subtotal", "grand total", "net total", "balance due", "balance",
        "tax", "vat", "gst", "hst", "pst", "sgst", "cgst", "igst", "service charge",
        "receipt", "invoice", "bill", "order #", "order no", "order number",
        "store #", "store no", "store number", "register", "terminal",
        "thank you", "thanks for", "come again", "have a nice", "please come back",
        // payment instruments
        "visa", "mastercard", "american express", "amex", "discover",
        "debit", "credit card", "credit", "cash", "change", "payment", "paid", "tender",
        "contactless", "chip & pin", "approved",
        // transaction identifiers
        "date", "time", "cashier", "transaction", "approval", "auth code",
        "reference", "ref no", "ticket", "sequence",
        // item / price headers
        "item", "qty", "quantity", "price", "amount", "discount", "savings",
        "description", "unit", "each",
        // contact / web
        "phone", "tel:", "fax:", "email:", "www.", "http", "contact us",
        // nutrition / product info
        "calories", "carbs", "protein"
    );

    // street-address tokens: any match means the line is an address
    private static final Pattern STREET_ADDRESS = Pattern.compile(
        "\\b(st|ave|rd|blvd|dr|ln|ct|pl|way|hwy|pkwy|suite|ste|fl|floor|unit|apt|po\\s+box)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // structured noise: GST/phone/email/URL
    private static final Pattern NOISE_PATTERN = Pattern.compile(
        "GSTIN|GTIN|\\bGST\\b|\\bCIN\\b|\\bPAN\\b|\\bTIN\\b|\\bEIN\\b|" +
        "VAT\\s+REG|Tel\\s*:|Ph\\s*:|Fax\\s*:|@|www\\.|http|" +
        "[0-9]{10,}|" +                               // long digit runs (phone/ref)
        "\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z\\d]Z[A-Z\\d]\\b",  // Indian GSTIN format
        Pattern.CASE_INSENSITIVE
    );

    // alias table, longest-pattern-first so partial matches don't shadow full ones
    private static final List<Map.Entry<Pattern, String>> ALIASES = List.of(
        entry("STARBUCKS\\s+COFFEE",           "STARBUCKS"),
        entry("WAL[\\-\\s]+MART\\s*SUPERCENTER","WALMART"),
        entry("WAL[\\-\\s]+MART",              "WALMART"),
        entry("MC\\s*DONALD[''S]*",            "MCDONALDS"),
        entry("KFC\\s+RESTAURANT",             "KFC"),
        entry("SUBWAY\\s+RESTAURANT",          "SUBWAY"),
        entry("DOMINO[''S]*\\s+PIZZA",         "DOMINOS"),
        entry("PIZZA\\s+HUT\\s+\\w+",          "PIZZA HUT"),
        entry("AMAZON\\.COM",                  "AMAZON"),
        entry("TARGET\\s+STORE",               "TARGET"),
        entry("WHOLE\\s+FOODS\\s+MARKET",      "WHOLE FOODS"),
        entry("COSTCO\\s+WHOLESALE",           "COSTCO"),
        entry("RELIANCE\\s+FRESH",             "RELIANCE FRESH"),
        entry("BIGBASKET\\.COM",               "BIGBASKET"),
        entry("SWIGGY\\s+[A-Z]+",             "SWIGGY"),
        entry("TATA\\s+CLIQ",                  "TATACLIQ"),
        // strip common header-noise prefixes
        entry("^WELCOME\\s+TO\\s+",            ""),
        entry("^THANK\\s+YOU\\s+FOR\\s+VISITING\\s+", ""),
        entry("^SHOP\\s+AT\\s+",               "")
    );

    private static Map.Entry<Pattern, String> entry(String regex, String replacement) {
        return Map.entry(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement);
    }

    // thresholds (package-visible for MerchantCandidate)
    static final double LOW_CONFIDENCE_THRESHOLD = 0.40;
    static final double AMBIGUITY_MARGIN          = 0.08;

    // best merchant name, or null if none survives exclusions
    public String extract(OcrDocument document) {
        MerchantCandidate c = extractWithScore(document);
        return c != null ? c.getName() : null;
    }

    // best candidate + score; ambiguous when the runner-up is within AMBIGUITY_MARGIN (AI-adjudication signal)
    public MerchantCandidate extractWithScore(OcrDocument document) {
        record Scored(OcrLine line, double score) {}

        List<Scored> ranked = document.getLines().stream()
            .map(l -> new Scored(l, scoreLine(l)))
            .filter(s -> s.score() > 0.0)
            .sorted(Comparator.comparingDouble(Scored::score).reversed())
            .toList();

        if (ranked.isEmpty()) {
            log.debug("MerchantExtractor: no candidate survived exclusions");
            return null;
        }

        Scored best = ranked.get(0);
        boolean ambiguous = ranked.size() > 1
            && (best.score() - ranked.get(1).score()) < AMBIGUITY_MARGIN;

        String name = normalize(best.line().getText());
        if (name.isEmpty()) return null;

        log.debug("MerchantExtractor: '{}' score={} conf={} relTop={} ambiguous={}",
            name,
            String.format("%.3f", best.score()),
            String.format("%.2f", best.line().getConfidence()),
            String.format("%.3f", best.line().relativeTop()),
            ambiguous);

        return MerchantCandidate.builder()
            .name(name)
            .score(best.score())
            .ambiguous(ambiguous)
            .build();
    }

    // top-N candidates by score, for AI fallback context
    public List<String> findCandidates(OcrDocument document, int limit) {
        return document.getLines().stream()
            .filter(l -> scoreLine(l) > 0.0)
            .sorted(Comparator.comparingDouble(l -> -scoreLine(l)))
            .limit(limit)
            .map(l -> normalize(l.getText()))
            .filter(n -> !n.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    double scoreLine(OcrLine line) {
        String text = line.getText() == null ? "" : line.getText().trim();
        if (text.length() < 2) return 0.0;
        if (isExcluded(text)) return 0.0;

        double score = 0.0;

        // position: top 25% of the document, decaying linearly
        double relTop = line.relativeTop();
        if (relTop <= 0.25) {
            score += 0.35 * (1.0 - relTop / 0.25);
        }

        // OCR confidence
        score += Math.min(1.0, line.getConfidence()) * 0.30;

        // capitalisation
        if (isAllCaps(text)) {
            score += 0.20;
        } else if (isTitleCase(text)) {
            score += 0.10;
        }

        // length sweet-spot 4-30
        int len = text.length();
        if (len >= 4 && len <= 30) {
            score += 0.10;
        } else if (len > 30 && len <= 60) {
            score += 0.04;
        } else if (len < 4) {
            return 0.0; // too short to be meaningful
        }

        // letter density
        long letters = text.chars().filter(Character::isLetter).count();
        if ((double) letters / len >= 0.75) {
            score += 0.05;
        }

        return score;
    }

    boolean isExcluded(String text) {
        String lower = text.toLowerCase(Locale.ROOT);

        for (String kw : EXCLUDED_SUBSTRINGS) {
            if (lower.contains(kw)) return true;
        }

        if (STREET_ADDRESS.matcher(text).find()) return true;
        if (NOISE_PATTERN.matcher(text).find())  return true;

        if (Character.isDigit(text.charAt(0))) return true;

        // majority digits
        long digits = text.chars().filter(Character::isDigit).count();
        if ((double) digits / text.length() > 0.5) return true;

        return false;
    }

    String normalize(String raw) {
        if (raw == null) return "";
        String result = raw.trim();

        for (Map.Entry<Pattern, String> alias : ALIASES) {
            result = alias.getKey().matcher(result).replaceAll(alias.getValue()).trim();
        }

        // strip leading/trailing punctuation
        result = result.replaceAll("^[^\\w]+|[^\\w)]+$", "").trim();

        result = result.replaceAll("\\s{2,}", " ");

        return result;
    }

    private boolean isAllCaps(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters < 2) return false;
        long upper = text.chars().filter(c -> Character.isLetter(c) && Character.isUpperCase(c)).count();
        return (double) upper / letters >= 0.85;
    }

    private boolean isTitleCase(String text) {
        String[] words = text.split("\\s+");
        if (words.length == 0) return false;
        int titleWords = 0;
        for (String w : words) {
            if (!w.isEmpty() && Character.isUpperCase(w.charAt(0))) titleWords++;
        }
        return (double) titleWords / words.length >= 0.6;
    }
}
