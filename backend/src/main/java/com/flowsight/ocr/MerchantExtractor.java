package com.flowsight.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Heuristic merchant-name extractor for OCR receipts: scores each line (position,
// OCR confidence, print size, caps, length, letter density), hard-excludes noise, cleans the name.
// Words are matched as whole tokens, so "TOTAL WINE & MORE" and "BILL'S DINER" stay candidates
// while "GRAND TOTAL", copy headers such as "ORIGINAL FOR RECIPIENT" and "Server: Mike" do not.
@Component
@Slf4j
public class MerchantExtractor {

    // a line made only of these words is a heading, label or boilerplate, never a merchant
    private static final Set<String> NON_MERCHANT_WORDS = Set.of(
        // copy headers
        "tax", "invoice", "simplified", "retail", "original", "recipient", "duplicate", "triplicate", "copy",
        "customer", "merchant", "memo", "supply", "receipt", "sales", "estimate", "kot", "order", "token",
        "proforma", "quotation", "challan", "statement", "abbreviated", "note", "bill", "cash", "credit", "debit",
        // amount labels
        "total", "grand", "sub", "subtotal", "net", "amount", "payable", "due", "balance", "vat", "gst", "cgst",
        "sgst", "igst", "hst", "pst", "change", "tendered", "tender", "paid", "payment", "discount", "savings",
        "service", "charge", "round", "off", "tip",
        // payment instruments
        "visa", "mastercard", "amex", "card", "upi", "mada", "approved", "contactless", "chip", "pin",
        // transaction metadata
        "date", "time", "cashier", "server", "table", "cover", "guest", "guests", "terminal", "register", "reg",
        "transaction", "approval", "auth", "code", "reference", "ref", "no", "number", "ticket", "sequence",
        "item", "items", "qty", "quantity", "price", "rate", "description", "unit", "each",
        // contact and courtesy
        "phone", "tel", "fax", "email", "mobile", "ph", "thank", "thanks", "you", "welcome", "visit", "visiting",
        "again", "come", "please", "shopping", "have", "nice", "day",
        "for", "to", "the", "of", "by", "at", "our", "your", "and", "a", "with", "us"
    );

    private static final List<String> NOISE_PHRASES = List.of(
        "thank you", "thanks for", "visit again", "come again", "www.", "http", "e-mail", "email:", "tel:",
        "ph:", "fax:", "phone:", "calories", "carbs", "protein", "contact us");

    // street-address tokens: any match means the line is an address
    private static final Pattern STREET_ADDRESS = Pattern.compile(
        "\\b(st|ave|rd|blvd|dr|ln|ct|pl|way|hwy|pkwy|suite|ste|fl|floor|unit|apt|po\\s+box|bldg)\\b",
        Pattern.CASE_INSENSITIVE
    );
    // spelled-out street words count only next to a comma or a number ("MG Road, ..."), not in "Street Food Co"
    private static final Pattern STREET_WORD = Pattern.compile(
        "(?i)\\b(road|street|avenue|lane|nagar|marg|chowk|bazaar)\\b.*[,\\d]|[,\\d].*\\b(road|street|avenue|lane|nagar|marg)\\b");

    // "City, ..." / "..., City" for the markets the app serves, plus 6-digit PIN and US state + ZIP
    private static final Pattern PLACE = Pattern.compile(
        "(?i:\\b(?:dubai|abu\\s+dhabi|sharjah|ajman|fujairah|riyadh|jeddah|khobar|dammam|makkah|madinah|"
            + "mumbai|bengaluru|bangalore|delhi|chennai|kolkata|hyderabad|pune|gurgaon|gurugram|noida|"
            + "new\\s+york|chicago|san\\s+francisco|los\\s+angeles)\\b)"
            + "|\\b\\d{6}\\b|\\b[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?\\b"
    );

    // structured noise: GST/phone/email/URL
    private static final Pattern NOISE_PATTERN = Pattern.compile(
        "GSTIN|GTIN|\\bGST\\b|\\bCIN\\b|\\bPAN\\b|\\bTIN\\b|\\bEIN\\b|\\bTRN\\b|" +
        "VAT\\s+(?:REG|NO)|Tel\\s*:|Ph\\s*:|Fax\\s*:|@|www\\.|http|" +
        "[0-9]{10,}|" +                               // long digit runs (phone/ref)
        "\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z\\d]Z[A-Z\\d]\\b",  // Indian GSTIN format
        Pattern.CASE_INSENSITIVE
    );

    // "Server: Mike", "Cashier: Anil", "Bill No: 4521"
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*[\\p{L}][\\p{L} .#]{0,18}[:!]\\s*\\S");
    // "Store #0042", "#0042 ANNAPOLIS MD", "Branch 12"
    private static final Pattern STORE_NUMBER_LINE = Pattern.compile(
        "^\\W*(?:#|(?:store|str|branch|outlet|shop|location)\\b\\s*(?:#|no\\.?|number)?\\s*\\d)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile("\\d[.,]\\d{2}(?!\\d)");

    private static final Pattern STORE_NUMBER_SUFFIX = Pattern.compile(
        "\\s*(?:\\b(?:store|str|branch)\\s*)?(?:#|\\bno\\.?\\s*)\\s*\\d+\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
        "(?:[\\s,]+(?:pvt\\.?|private|ltd\\.?|limited|llc|l\\.l\\.c\\.?|inc\\.?|co\\.|corp\\.?|llp|fze|fzco|plc|est\\.?))+\\s*$",
        Pattern.CASE_INSENSITIVE);

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
        List<Scored> ranked = rank(document);
        if (ranked.isEmpty()) {
            log.debug("MerchantExtractor: no candidate survived exclusions");
            return null;
        }

        Scored best = ranked.get(0);
        boolean ambiguous = ranked.size() > 1
            && (best.score() - ranked.get(1).score()) < AMBIGUITY_MARGIN;

        String name = normalize(best.line().getText());
        if (name.isEmpty()) return null;

        log.debug("MerchantExtractor: score={} conf={} relTop={} ambiguous={}",
            String.format("%.3f", best.score()),
            String.format("%.2f", best.line().getConfidence()),
            String.format("%.3f", best.line().relativeTop()),
            ambiguous);

        return MerchantCandidate.builder()
            .name(name)
            .rawText(best.line().getText().trim())
            .score(best.score())
            .ambiguous(ambiguous)
            .build();
    }

    // top-N candidates by score, for AI fallback context
    public List<String> findCandidates(OcrDocument document, int limit) {
        return rank(document).stream()
            .limit(limit)
            .map(s -> normalize(s.line().getText()))
            .filter(n -> !n.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    private record Scored(OcrLine line, double score) {}

    private List<Scored> rank(OcrDocument document) {
        List<OcrLine> lines = document.getLines();
        double medianHeight = medianHeight(lines);
        int firstAmountLine = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getText() != null && MONEY.matcher(lines.get(i).getText()).find()) {
                firstAmountLine = i;
                break;
            }
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            double s = scoreLine(lines.get(i), medianHeight);
            // the merchant heads the receipt; lines below the first priced line rarely are it
            if (i > firstAmountLine) s *= 0.5;
            if (s > 0.0) scored.add(new Scored(lines.get(i), s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored;
    }

    double scoreLine(OcrLine line) {
        return scoreLine(line, 0.0);
    }

    double scoreLine(OcrLine line, double medianHeight) {
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

        // printed larger than the body text (merchant names usually are)
        if (medianHeight > 0 && line.getHeightPx() >= 1.2 * medianHeight) {
            score += 0.15;
        }

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

        for (String phrase : NOISE_PHRASES) {
            if (lower.contains(phrase)) return true;
        }
        if (Character.isDigit(text.charAt(0))) return true;

        // majority digits
        long digits = text.chars().filter(Character::isDigit).count();
        if ((double) digits / text.length() > 0.5) return true;

        if (MONEY.matcher(text).find()) return true;
        if (KEY_VALUE.matcher(text).find()) return true;
        if (STORE_NUMBER_LINE.matcher(text).find()) return true;
        if (STREET_ADDRESS.matcher(text).find()) return true;
        if (STREET_WORD.matcher(text).find()) return true;
        if (PLACE.matcher(text).find()) return true;
        if (NOISE_PATTERN.matcher(text).find()) return true;

        List<String> words = ReceiptLabels.tokens(text).stream().filter(w -> w.length() > 1 || w.equals("a")).toList();
        return words.isEmpty() || NON_MERCHANT_WORDS.containsAll(words);
    }

    /** True when the text is only heading, label or boilerplate words, e.g. "TAX INVOICE". */
    public boolean isHeading(String text) {
        List<String> words = ReceiptLabels.tokens(text).stream().filter(w -> w.length() > 1).toList();
        return words.isEmpty() || NON_MERCHANT_WORDS.containsAll(words);
    }

    public String clean(String raw) {
        return normalize(raw);
    }

    String normalize(String raw) {
        if (raw == null) return "";
        String result = raw.trim();

        for (Map.Entry<Pattern, String> alias : ALIASES) {
            result = alias.getKey().matcher(result).replaceAll(alias.getValue()).trim();
        }

        result = STORE_NUMBER_SUFFIX.matcher(result).replaceAll("").trim();
        String withoutLegal = LEGAL_SUFFIX.matcher(result).replaceAll("").trim();
        if (!withoutLegal.isEmpty()) result = withoutLegal;

        // strip leading/trailing punctuation
        result = result.replaceAll("^[^\\w]+|[^\\w)]+$", "").trim();

        result = result.replaceAll("\\s{2,}", " ");

        return result;
    }

    private static double medianHeight(List<OcrLine> lines) {
        double[] h = lines.stream().mapToDouble(OcrLine::getHeightPx).filter(v -> v > 0).sorted().toArray();
        return h.length == 0 ? 0.0 : h[h.length / 2];
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
