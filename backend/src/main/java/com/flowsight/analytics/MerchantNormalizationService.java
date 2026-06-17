package com.flowsight.analytics;

import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Probabilistic merchant normalization used by recurring detection and analytics.
 *
 * <p>The service answers the question: given two merchant strings, are they the same
 * brand? It returns a {@link Normalized} struct containing:
 * <ul>
 *   <li>{@code key} — a grouping key used to bucket transactions ("netflix")</li>
 *   <li>{@code canonicalName} — a display name if the merchant matched a known brand
 *       ("Netflix"), otherwise {@code null} (use the original raw string)</li>
 * </ul>
 *
 * <p>Strategy (deterministic, no LLM):
 * <ol>
 *   <li>Lowercase + strip URL prefixes ({@code www.}, {@code .com}, {@code .in}, etc.)</li>
 *   <li>Remove long digit sequences (≥5 chars — transaction reference numbers)</li>
 *   <li>Strip UPI prefixes, parentheticals, and known noise suffixes
 *       ({@code subscription}, {@code monthly}, {@code premium}, ...)</li>
 *   <li>Apply brand alias map (substring match → canonical name)</li>
 *   <li>If no alias match, use the first meaningful word(s) as the grouping key</li>
 * </ol>
 *
 * <p>Examples:
 * <pre>
 *   "NETFLIX"             → key=netflix, canonical=Netflix
 *   "NETFLIX.COM"         → key=netflix, canonical=Netflix
 *   "Netflix Subscription"→ key=netflix, canonical=Netflix
 *   "ZOMATO 123456 UPI"   → key=zomato,  canonical=Zomato
 *   "FOOBAR BAKERY"       → key=foobar,  canonical=null  (no alias)
 * </pre>
 */
@Service
public class MerchantNormalizationService {

    /**
     * Brand alias map — insertion order matters (LinkedHashMap).
     * Substring of normalized text (after suffix stripping) → canonical display name.
     * Keep entries short and broad; first match wins.
     */
    private static final Map<String, String> BRAND_ALIASES = new LinkedHashMap<>();
    static {
        // ============================================================
        // RULE: most-specific (multi-word) aliases first, then single
        // word brand keywords. The first matching alias wins, so this
        // ordering matters for ambiguous strings.
        // ============================================================

        // --- Multi-word subscription services (must come first) ---
        BRAND_ALIASES.put("youtube premium", "YouTube Premium");
        BRAND_ALIASES.put("yt premium",      "YouTube Premium");
        BRAND_ALIASES.put("amazon prime",    "Amazon Prime");
        BRAND_ALIASES.put("prime video",     "Amazon Prime");
        BRAND_ALIASES.put("amzn prime",      "Amazon Prime");
        BRAND_ALIASES.put("apple music",     "Apple Music");
        BRAND_ALIASES.put("apple icloud",    "Apple iCloud");
        BRAND_ALIASES.put("microsoft 365",   "Microsoft 365");
        BRAND_ALIASES.put("office 365",      "Microsoft 365");
        BRAND_ALIASES.put("swiggy instamart","Swiggy Instamart");
        BRAND_ALIASES.put("instamart",       "Swiggy Instamart"); // before "swiggy" single-word
        BRAND_ALIASES.put("vi recharge",     "Vi");

        // --- Single-word subscription brands ---
        BRAND_ALIASES.put("netflix",         "Netflix");
        BRAND_ALIASES.put("spotify",         "Spotify");
        BRAND_ALIASES.put("disney",          "Disney+ Hotstar");
        BRAND_ALIASES.put("hotstar",         "Disney+ Hotstar");
        BRAND_ALIASES.put("icloud",          "Apple iCloud");
        BRAND_ALIASES.put("adobe",           "Adobe");
        BRAND_ALIASES.put("github",          "GitHub");
        BRAND_ALIASES.put("openai",          "OpenAI");
        BRAND_ALIASES.put("chatgpt",         "OpenAI");
        BRAND_ALIASES.put("anthropic",       "Anthropic");
        BRAND_ALIASES.put("claude",          "Anthropic");
        BRAND_ALIASES.put("dropbox",         "Dropbox");
        BRAND_ALIASES.put("notion",          "Notion");
        BRAND_ALIASES.put("linkedin",        "LinkedIn");
        BRAND_ALIASES.put("canva",           "Canva");
        BRAND_ALIASES.put("figma",           "Figma");

        // --- Indian consumer brands ---
        BRAND_ALIASES.put("zomato",          "Zomato");
        BRAND_ALIASES.put("swiggy",          "Swiggy");
        BRAND_ALIASES.put("blinkit",         "Blinkit");
        BRAND_ALIASES.put("bigbasket",       "BigBasket");
        BRAND_ALIASES.put("dunzo",           "Dunzo");
        BRAND_ALIASES.put("zepto",           "Zepto");
        BRAND_ALIASES.put("amazon",          "Amazon");
        BRAND_ALIASES.put("amzn",            "Amazon");
        BRAND_ALIASES.put("flipkart",        "Flipkart");
        BRAND_ALIASES.put("meesho",          "Meesho");
        BRAND_ALIASES.put("myntra",          "Myntra");
        BRAND_ALIASES.put("nykaa",           "Nykaa");
        BRAND_ALIASES.put("uber",            "Uber");
        BRAND_ALIASES.put("ola",             "Ola");
        BRAND_ALIASES.put("rapido",          "Rapido");
        BRAND_ALIASES.put("irctc",           "IRCTC");
        BRAND_ALIASES.put("makemytrip",      "MakeMyTrip");
        BRAND_ALIASES.put("goibibo",         "Goibibo");

        // --- Telecom and utility providers (brand-specific) ---
        BRAND_ALIASES.put("tata power",      "Tata Power");
        BRAND_ALIASES.put("adani electric",  "Adani Electricity");
        BRAND_ALIASES.put("torrent power",   "Torrent Power");
        BRAND_ALIASES.put("jio",             "Jio");
        BRAND_ALIASES.put("airtel",          "Airtel");
        BRAND_ALIASES.put("vodafone",        "Vi");
        BRAND_ALIASES.put("bsnl",            "BSNL");
        BRAND_ALIASES.put("bescom",          "BESCOM");

        // --- Conceptual markers (must check before bank names so e.g. "EMI ICICI"
        // resolves to EMI rather than ICICI Bank) ---
        BRAND_ALIASES.put("emi",             "EMI");
        BRAND_ALIASES.put("home loan",       "Loan EMI");
        BRAND_ALIASES.put("car loan",        "Loan EMI");
        BRAND_ALIASES.put("personal loan",   "Loan EMI");
        BRAND_ALIASES.put("loan",            "Loan EMI");
        BRAND_ALIASES.put("rent",            "Rent");
        BRAND_ALIASES.put("electricity",     "Electricity");
        BRAND_ALIASES.put("electric",        "Electricity");
        BRAND_ALIASES.put("gas bill",        "Gas");
        BRAND_ALIASES.put("water bill",      "Water");
        BRAND_ALIASES.put("broadband",       "Broadband");
        BRAND_ALIASES.put("internet",        "Broadband");

        // --- Banks ---
        BRAND_ALIASES.put("yes bank",        "Yes Bank");
        BRAND_ALIASES.put("idfc first",      "IDFC FIRST Bank");
        BRAND_ALIASES.put("idfc",            "IDFC FIRST Bank");
        BRAND_ALIASES.put("hdfc",            "HDFC Bank");
        BRAND_ALIASES.put("icici",           "ICICI Bank");
        BRAND_ALIASES.put("sbi",             "SBI");
        BRAND_ALIASES.put("axis",            "Axis Bank");
        BRAND_ALIASES.put("kotak",           "Kotak Bank");
        BRAND_ALIASES.put("rbl",             "RBL Bank");

        // --- Fitness ---
        BRAND_ALIASES.put("cure.fit",        "cure.fit");
        BRAND_ALIASES.put("curefit",         "cure.fit");
        BRAND_ALIASES.put("cult.fit",        "cure.fit");
        BRAND_ALIASES.put("gold gym",        "Gold's Gym");
        BRAND_ALIASES.put("anytime fit",     "Anytime Fitness");
    }

    /** Subscription/transaction suffixes stripped from the end of normalized strings. */
    private static final String[] NOISE_SUFFIXES = {
        "subscription", "subs", "monthly", "annual", "yearly", "quarterly",
        "premium", "pro", "plus", "plan", "membership", "renewal",
        "payment", "autopay", "auto pay", "auto debit", "ach", "recurring",
        "bill payment", "bill", "recharge", "refill", "topup", "top up",
        "checkout", "purchase",
        "individual", "family", "student"
    };

    /** Words skipped when picking the "first meaningful word" fallback grouping key. */
    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "to", "from", "for", "of", "and", "or",
        "pvt", "ltd", "inc", "llc", "co", "company", "corp", "corporation",
        "store", "shop", "online", "india", "in", "ind",
        "payment", "txn", "transaction", "ref"
    );

    /** Pre-compiled patterns for performance. */
    private static final Pattern TLD_PATTERN          = Pattern.compile("\\.(com|in|co\\.in|net|org|io|app|co|tv|me|edu|ai)\\b");
    private static final Pattern LONG_DIGITS_PATTERN  = Pattern.compile("\\b\\d{5,}\\b");
    private static final Pattern UPI_PREFIX_PATTERN   = Pattern.compile("\\b(upi|imps|neft|rtgs|ach|paytm|gpay|phonepe|bhim)[/\\-:]?");
    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\(.*?\\)");
    private static final Pattern SEPARATOR_PATTERN    = Pattern.compile("[/_\\-]+");
    private static final Pattern NON_ALPHANUM_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern WHITESPACE_PATTERN   = Pattern.compile("\\s+");

    // Public API

    /**
     * Normalizes a raw merchant string for grouping and display.
     * Never returns null; returns {@link Normalized#empty()} for blank input.
     */
    public Normalized normalize(String raw) {
        if (raw == null || raw.isBlank()) return Normalized.empty();

        String s = raw.toLowerCase().trim();

        // Step 1: strip URL prefixes and TLDs
        if (s.startsWith("www.")) s = s.substring(4);
        s = TLD_PATTERN.matcher(s).replaceAll("");

        // Step 2: strip transaction artifacts
        s = LONG_DIGITS_PATTERN.matcher(s).replaceAll("");
        s = UPI_PREFIX_PATTERN.matcher(s).replaceAll("");
        s = PARENTHETICAL_PATTERN.matcher(s).replaceAll(" ");

        // Step 3: normalize separators and strip non-alphanumeric
        s = SEPARATOR_PATTERN.matcher(s).replaceAll(" ");
        s = NON_ALPHANUM_PATTERN.matcher(s).replaceAll(" ");
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ").trim();

        if (s.isEmpty()) return Normalized.empty();

        // Step 4a: brand alias check on PRE-stripped text — catches multi-word brands
        // whose key contains a suffix word (e.g. "youtube premium").
        Normalized preMatch = matchAlias(s);
        if (preMatch != null) return preMatch;

        // Step 4b: strip noise suffixes (repeated until none match)
        String stripped = stripSuffixes(s);

        // Step 5: brand alias check on suffix-stripped text
        Normalized postMatch = matchAlias(stripped);
        if (postMatch != null) return postMatch;

        // Step 6: fallback — use first 1-2 meaningful words as key
        String key = pickKey(stripped);
        return new Normalized(key, null);
    }

    private Normalized matchAlias(String text) {
        for (Map.Entry<String, String> entry : BRAND_ALIASES.entrySet()) {
            if (text.contains(entry.getKey())) {
                String canonical = entry.getValue();
                return new Normalized(canonical.toLowerCase().replaceAll("[^a-z0-9]", ""), canonical);
            }
        }
        return null;
    }

    /** Convenience: returns only the grouping key. */
    public String normalizeKey(String raw) {
        return normalize(raw).getKey();
    }

    private String stripSuffixes(String s) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : NOISE_SUFFIXES) {
                if (s.endsWith(" " + suffix)) {
                    s = s.substring(0, s.length() - suffix.length() - 1).trim();
                    changed = true;
                    break;
                }
                if (s.equals(suffix)) {
                    s = "";
                    changed = true;
                    break;
                }
            }
        }
        return s;
    }

    /**
     * Picks the grouping key from the cleaned merchant string by joining
     * up to two leading non-stopword tokens.  Falls back to the cleaned
     * string with spaces removed if no meaningful word is found.
     */
    private String pickKey(String s) {
        String[] tokens = s.split(" ");
        StringBuilder key = new StringBuilder();
        int meaningful = 0;

        for (String token : tokens) {
            if (token.length() < 2)          continue;
            if (STOPWORDS.contains(token))   continue;
            key.append(token);
            meaningful++;
            if (meaningful >= 2 || key.length() >= 12) break;
        }

        return key.length() > 0 ? key.toString() : s.replace(" ", "");
    }

    // Result type

    @Value
    public static class Normalized {
        String key;
        /** Display name from the alias map; null when no alias matched. */
        String canonicalName;

        public static Normalized empty() {
            return new Normalized("", null);
        }

        public boolean isEmpty() {
            return key.isEmpty();
        }
    }
}
