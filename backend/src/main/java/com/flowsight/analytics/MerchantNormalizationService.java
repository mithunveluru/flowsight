package com.flowsight.analytics;

import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Deterministic merchant normalization for grouping and display (no LLM).
@Service
public class MerchantNormalizationService {

    // substring of normalized text -> canonical name; most-specific first, first match wins
    private static final Map<String, String> BRAND_ALIASES = new LinkedHashMap<>();
    static {
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

        BRAND_ALIASES.put("tata power",      "Tata Power");
        BRAND_ALIASES.put("adani electric",  "Adani Electricity");
        BRAND_ALIASES.put("torrent power",   "Torrent Power");
        BRAND_ALIASES.put("jio",             "Jio");
        BRAND_ALIASES.put("airtel",          "Airtel");
        BRAND_ALIASES.put("vodafone",        "Vi");
        BRAND_ALIASES.put("bsnl",            "BSNL");
        BRAND_ALIASES.put("bescom",          "BESCOM");

        // before bank names so "EMI ICICI" resolves to EMI, not ICICI Bank
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

        BRAND_ALIASES.put("yes bank",        "Yes Bank");
        BRAND_ALIASES.put("idfc first",      "IDFC FIRST Bank");
        BRAND_ALIASES.put("idfc",            "IDFC FIRST Bank");
        BRAND_ALIASES.put("hdfc",            "HDFC Bank");
        BRAND_ALIASES.put("icici",           "ICICI Bank");
        BRAND_ALIASES.put("sbi",             "SBI");
        BRAND_ALIASES.put("axis",            "Axis Bank");
        BRAND_ALIASES.put("kotak",           "Kotak Bank");
        BRAND_ALIASES.put("rbl",             "RBL Bank");

        BRAND_ALIASES.put("cure.fit",        "cure.fit");
        BRAND_ALIASES.put("curefit",         "cure.fit");
        BRAND_ALIASES.put("cult.fit",        "cure.fit");
        BRAND_ALIASES.put("gold gym",        "Gold's Gym");
        BRAND_ALIASES.put("anytime fit",     "Anytime Fitness");
    }

    // suffixes stripped from the end of normalized strings
    private static final String[] NOISE_SUFFIXES = {
        "subscription", "subs", "monthly", "annual", "yearly", "quarterly",
        "premium", "pro", "plus", "plan", "membership", "renewal",
        "payment", "autopay", "auto pay", "auto debit", "ach", "recurring",
        "bill payment", "bill", "recharge", "refill", "topup", "top up",
        "checkout", "purchase",
        "individual", "family", "student"
    };

    // skipped when picking the fallback grouping key
    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "to", "from", "for", "of", "and", "or",
        "pvt", "ltd", "inc", "llc", "co", "company", "corp", "corporation",
        "store", "shop", "online", "india", "in", "ind",
        "payment", "txn", "transaction", "ref"
    );

    private static final Pattern TLD_PATTERN          = Pattern.compile("\\.(com|in|co\\.in|net|org|io|app|co|tv|me|edu|ai)\\b");
    private static final Pattern LONG_DIGITS_PATTERN  = Pattern.compile("\\b\\d{5,}\\b");
    private static final Pattern UPI_PREFIX_PATTERN   = Pattern.compile("\\b(upi|imps|neft|rtgs|ach|paytm|gpay|phonepe|bhim)[/\\-:]?");
    private static final Pattern PARENTHETICAL_PATTERN = Pattern.compile("\\(.*?\\)");
    private static final Pattern SEPARATOR_PATTERN    = Pattern.compile("[/_\\-]+");
    private static final Pattern NON_ALPHANUM_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern WHITESPACE_PATTERN   = Pattern.compile("\\s+");

    // Never returns null; returns Normalized.empty() for blank input.
    public Normalized normalize(String raw) {
        if (raw == null || raw.isBlank()) return Normalized.empty();

        String s = raw.toLowerCase().trim();

        if (s.startsWith("www.")) s = s.substring(4);
        s = TLD_PATTERN.matcher(s).replaceAll("");

        s = LONG_DIGITS_PATTERN.matcher(s).replaceAll("");
        s = UPI_PREFIX_PATTERN.matcher(s).replaceAll("");
        s = PARENTHETICAL_PATTERN.matcher(s).replaceAll(" ");

        s = SEPARATOR_PATTERN.matcher(s).replaceAll(" ");
        s = NON_ALPHANUM_PATTERN.matcher(s).replaceAll(" ");
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ").trim();

        if (s.isEmpty()) return Normalized.empty();

        // alias check before suffix stripping catches multi-word brands like "youtube premium"
        Normalized preMatch = matchAlias(s);
        if (preMatch != null) return preMatch;

        String stripped = stripSuffixes(s);

        Normalized postMatch = matchAlias(stripped);
        if (postMatch != null) return postMatch;

        // fallback: first meaningful words
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

    // key = up to two leading non-stopword tokens
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

    @Value
    public static class Normalized {
        String key;
        // null when no alias matched
        String canonicalName;

        public static Normalized empty() {
            return new Normalized("", null);
        }

        public boolean isEmpty() {
            return key.isEmpty();
        }
    }
}
