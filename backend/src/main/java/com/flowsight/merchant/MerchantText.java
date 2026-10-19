package com.flowsight.merchant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns bank, card and receipt merchant descriptors into a readable merchant name
 * ({@link #clean}) and into the lower-case token form used for alias matching and
 * grouping keys ({@link #matchText}). Pure and deterministic.
 */
public final class MerchantText {

    private MerchantText() {
    }

    private static final int CASE = Pattern.CASE_INSENSITIVE;

    // UPI/DR/<ref>/<payee>/<bank>/<vpa>/<note>, UPI-<payee>-<vpa>-<ifsc>-<ref>-UPI, NEFT-<ifsc>-<payee>, BIL/BPAY/<ref>/<biller>
    private static final Pattern RAIL_START = Pattern.compile("^(UPI|IMPS|NEFT|RTGS|BIL|MMT)\\s*[/\\-]", CASE);
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$", CASE);
    private static final Set<String> RAIL_WORDS = Set.of(
        "UPI", "DR", "CR", "IMPS", "NEFT", "RTGS", "BIL", "BPAY", "ONL", "MMT", "P2A", "P2M", "PAY", "COLLECT");
    // the four-letter IFSC bank codes that UPI descriptors carry as their own segment
    private static final Set<String> BANK_CODES = Set.of(
        "HDFC", "ICIC", "SBIN", "UTIB", "YESB", "KKBK", "PUNB", "BARB", "CNRB", "UBIN", "IDIB", "IOBA",
        "INDB", "IDFB", "FDRL", "RATN", "AUBL", "PYTM", "AIRP", "BKID", "MAHB", "CBIN", "KARB", "SIBL");

    private static final Pattern[] LEADING = {
        Pattern.compile("^ACH\\s+D-?\\s+TP\\s+ACH\\s+", CASE),
        Pattern.compile("^(POS|MADA|ONLINE|DEBIT\\s+CARD|CREDIT\\s+CARD|CARD|INTERNET|E-?COMMERCE)\\s+(PURCHASE|PURCH|DEBIT|TXN)\\s+", CASE),
        Pattern.compile("^ECOM\\s+PUR(CHASE)?\\s+", CASE),
        Pattern.compile("^CHECKCARD\\s+\\d{4}\\s+", CASE),
        Pattern.compile("^(CHECKCARD|POS|ECOM|MADA|E-?COMMERCE|BBPS|PURCHASE|VISA|DEBIT)\\s+", CASE),
        Pattern.compile("^(APPLE|GOOGLE|SAMSUNG)\\s+PAY\\s+", CASE),
        Pattern.compile("^(ACH|SADAD|BILL)\\s+(DEBIT|CREDIT|PAYMENT|PAY)\\s+", CASE),
        Pattern.compile("^(ACH|SADAD|BILLPAY)\\s+", CASE),
        Pattern.compile("^STANDING\\s+ORDER\\s+", CASE),
        Pattern.compile("^(SQ|TST|SP|PAYPAL|GOOGLE|PP)\\s*\\*\\s*", CASE),
        Pattern.compile("^(UPI|NEFT|IMPS|VPA|POS|ATM|ACH|SI|ECS|DD|NACH)-", CASE),
    };

    private static final Pattern MASKED_CARD  = Pattern.compile("\\b\\d{0,6}X{3,}\\d{0,4}\\b", CASE);
    private static final Pattern PHONE        = Pattern.compile("\\b\\d{3}-\\d{3}-\\d{4}\\b");
    private static final Pattern PPD_ID       = Pattern.compile("\\bPPD\\s+ID:?\\s*\\S*", CASE);
    private static final Pattern WEB_PAYMENT  = Pattern.compile("\\bWEB\\s+(PMTS?|PAYMENTS?)\\b", CASE);
    private static final Pattern STORE_NUMBER = Pattern.compile("(\\bSTORE\\s*)?#\\s*\\d+", CASE);
    private static final Pattern PARENTHESES  = Pattern.compile("\\([^)]*\\)");
    private static final Pattern WWW          = Pattern.compile("\\bwww\\.", CASE);
    private static final Pattern DOMAIN       = Pattern.compile("(?<=\\w)\\.(com|co\\.in|in|net|org|io|app|co|ae|sa|us)\\b", CASE);
    private static final Pattern LONG_NUMBER  = Pattern.compile("(?<![\\p{L}\\d])\\d{4,}(?![\\p{L}\\d])");
    private static final Pattern LOOSE_DASH   = Pattern.compile("(^|\\s)-+|-+(\\s|$)|-(?=\\d)");
    private static final Pattern SEPARATORS   = Pattern.compile("[/_|\\\\]+");
    private static final Pattern SPACES       = Pattern.compile("\\s+");
    private static final Pattern EDGE_JUNK    = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}.'&+]+$");

    private static final Set<String> TRAILING_NOISE = Set.of(
        "PAYMENT", "PAYMENTS", "PMT", "PMTS", "BILL", "AUTOPAY", "ORDER", "INSTALMENT", "INSTALLMENT",
        "CHARGE", "CHARGES", "FEE", "FEES", "FINES", "PURCHASE", "DEBIT", "TXN", "REF", "UPI", "NEFT", "IMPS",
        "PVT", "LTD", "PRIVATE", "LIMITED", "INC", "LLC", "LLP", "PLC", "CO", "CORP", "CORPORATION",
        "IN", "IND", "SA", "KSA", "AE", "UAE", "US", "USA");
    private static final Set<String> US_STATES = Set.of(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
        "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC",
        "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY");
    private static final List<String[]> CITIES = cities(
        // India
        "MUMBAI", "BOMBAY", "NEW DELHI", "DELHI", "BANGALORE", "BENGALURU", "HYDERABAD", "CHENNAI", "PUNE",
        "KOLKATA", "GURGAON", "GURUGRAM", "NOIDA", "AHMEDABAD", "JAIPUR", "LUCKNOW", "KOCHI", "THANE",
        "NAVI MUMBAI", "CHANDIGARH", "INDORE", "COIMBATORE", "NAGPUR", "SURAT", "MYSORE",
        // Saudi Arabia
        "RIYADH", "JEDDAH", "DAMMAM", "AL KHOBAR", "KHOBAR", "MAKKAH", "MECCA", "MADINAH", "MEDINA", "DHAHRAN",
        "TAIF", "TABUK", "ABHA", "JUBAIL",
        // United Arab Emirates
        "DUBAI", "ABU DHABI", "SHARJAH", "AJMAN", "AL AIN", "FUJAIRAH", "RAS AL KHAIMAH", "UMM AL QUWAIN",
        // United States
        "NEW YORK", "SAN FRANCISCO", "LOS ANGELES", "CHICAGO", "AUSTIN", "SEATTLE", "BOSTON", "HOUSTON",
        "DALLAS", "MIAMI", "ATLANTA", "DENVER", "PHOENIX", "PORTLAND", "SAN DIEGO", "SAN JOSE", "BROOKLYN",
        "PHILADELPHIA", "WASHINGTON", "ANNAPOLIS", "OAKLAND");
    private static final Set<String> CITY_GUARDS = Set.of("OF", "THE", "DE", "AND", "&");

    /** Readable merchant name, or "" when nothing merchant-like is left. Never null. */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (s.isEmpty()) return "";

        if (RAIL_START.matcher(s).find()) s = railPayee(s);
        s = stripLeading(s);

        s = PPD_ID.matcher(s).replaceAll(" ");
        s = WEB_PAYMENT.matcher(s).replaceAll(" ");
        s = PHONE.matcher(s).replaceAll(" ");
        s = MASKED_CARD.matcher(s).replaceAll(" ");
        s = STORE_NUMBER.matcher(s).replaceAll(" ");
        s = PARENTHESES.matcher(s).replaceAll(" ");
        s = WWW.matcher(s).replaceAll("");
        s = DOMAIN.matcher(s).replaceAll(" ");
        s = beforeAsterisk(s);
        s = SEPARATORS.matcher(s).replaceAll(" ");
        s = LOOSE_DASH.matcher(s).replaceAll(" ");
        s = LONG_NUMBER.matcher(s).replaceAll(" ");

        List<String> tokens = new ArrayList<>();
        for (String t : SPACES.split(s.trim())) {
            String token = t.equals("&") ? t : EDGE_JUNK.matcher(t).replaceAll("");
            if (!token.isEmpty() && !isReference(token)) tokens.add(token);
        }
        stripTrailing(tokens);
        return display(String.join(" ", tokens));
    }

    /** Lower-case alias/grouping form: apostrophes dropped, domains and punctuation removed. */
    public static String matchText(String text) {
        if (text == null || text.isBlank()) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("['’`]", "");
        s = WWW.matcher(s).replaceAll("");
        s = DOMAIN.matcher(s).replaceAll("");
        s = s.replaceAll("[^\\p{L}\\p{N}]+", " ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    // UPI and bill-pay strings carry the payee as one segment among references, bank codes and handles.
    private static String railPayee(String s) {
        String[] parts = s.contains("/") ? s.split("/") : s.split("-");
        for (String part : parts) {
            String p = part.trim();
            String upper = p.toUpperCase(Locale.ROOT);
            if (p.isEmpty() || RAIL_WORDS.contains(upper) || BANK_CODES.contains(upper)) continue;
            if (p.contains("@") || IFSC.matcher(p).matches() || !p.matches(".*\\p{L}.*")) continue;
            if (upper.startsWith("PAYMENT FROM") || upper.startsWith("SENT USING")) continue;
            return p;
        }
        return s;
    }

    private static String stripLeading(String s) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Pattern p : LEADING) {
                Matcher m = p.matcher(s);
                if (m.find() && m.end() < s.length() && !s.substring(m.end()).isBlank()) {
                    s = s.substring(m.end()).trim();
                    changed = true;
                }
            }
        }
        return s;
    }

    // "SWIGGY*ORDER 123", "AMZN Mktp US*95A2M62": the brand comes before the asterisk.
    private static String beforeAsterisk(String s) {
        int star = s.indexOf('*');
        if (star < 0) return s;
        String left = s.substring(0, star).trim();
        return left.replaceAll("[^\\p{L}]", "").length() >= 2 ? left : s.replace('*', ' ');
    }

    // Reference-like tokens: long, at least three digits, and digit-dense (P1A2B3C4, UTIB0000601).
    private static boolean isReference(String token) {
        if (token.length() < 6) return false;
        long digits = token.chars().filter(Character::isDigit).count();
        long letters = token.chars().filter(Character::isLetter).count();
        return digits >= 3 && letters > 0 && digits >= 0.35 * token.length();
    }

    private static void stripTrailing(List<String> tokens) {
        boolean changed = true;
        while (changed && tokens.size() > 1) {
            changed = false;
            String last = tokens.get(tokens.size() - 1).toUpperCase(Locale.ROOT).replace(".", "");
            if (TRAILING_NOISE.contains(last) || US_STATES.contains(last)) {
                tokens.remove(tokens.size() - 1);
                changed = true;
                continue;
            }
            for (String[] city : CITIES) {
                int n = city.length;
                if (tokens.size() <= n || !endsWith(tokens, city)) continue;
                if (CITY_GUARDS.contains(tokens.get(tokens.size() - n - 1).toUpperCase(Locale.ROOT))) continue;
                for (int i = 0; i < n; i++) tokens.remove(tokens.size() - 1);
                changed = true;
                break;
            }
        }
    }

    private static boolean endsWith(List<String> tokens, String[] words) {
        int offset = tokens.size() - words.length;
        for (int i = 0; i < words.length; i++) {
            if (!tokens.get(offset + i).equalsIgnoreCase(words[i])) return false;
        }
        return true;
    }

    // All-caps descriptors become title case; text that already has lower case is kept as typed.
    static String display(String s) {
        if (s.isEmpty() || !s.equals(s.toUpperCase(Locale.ROOT))) return s;
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (out.length() > 0) out.append(' ');
            out.append(displayWord(word, out.length() == 0));
        }
        return out.toString();
    }

    private static String displayWord(String word, boolean first) {
        if (word.chars().anyMatch(Character::isDigit) || word.contains("&")) return word;
        String letters = word.replaceAll("[^\\p{L}]", "");
        boolean hasVowel = letters.matches(".*[AEIOUY].*");
        if (letters.length() <= 2 && !Set.of("OF", "AND", "THE", "DE", "AL").contains(letters)) return word;
        if (letters.length() <= 6 && !hasVowel) return word;
        String lower = word.toLowerCase(Locale.ROOT);
        if (!first && Set.of("of", "and", "the", "de").contains(lower)) return lower;
        StringBuilder b = new StringBuilder(lower);
        boolean upNext = true;
        for (int i = 0; i < b.length(); i++) {
            char c = b.charAt(i);
            if (Character.isLetter(c) && upNext) {
                b.setCharAt(i, Character.toUpperCase(c));
                upNext = false;
            } else if (c == '-' || c == '.') {
                upNext = true;
            } else if (c == '\'') {
                upNext = false;
            }
        }
        return b.toString();
    }

    private static List<String[]> cities(String... names) {
        List<String[]> list = new ArrayList<>();
        for (String n : names) list.add(n.split(" "));
        // longest first so "NEW DELHI" is removed whole before "DELHI"
        list.sort((a, b) -> Integer.compare(b.length, a.length));
        return List.copyOf(list);
    }
}
