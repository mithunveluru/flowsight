package com.flowsight.analytics;

import com.flowsight.merchant.AliasIndex;
import com.flowsight.merchant.MerchantText;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Deterministic merchant normalization for grouping and display (no LLM).
// The built-in aliases are also the seed of the merchant_aliases table (V16).
@Service
public class MerchantNormalizationService {

    // {region, alias, canonical}; region "" = global. Priority is list order: the first alias
    // whose tokens appear in the text wins, so specific names come before generic words.
    private static final String[][] BUILT_IN = {
        // multi-word names that contain a shorter alias
        {"", "youtube premium",   "YouTube Premium"},
        {"", "yt premium",        "YouTube Premium"},
        {"", "amazon prime",      "Amazon Prime"},
        {"", "prime video",       "Amazon Prime"},
        {"", "amzn prime",        "Amazon Prime"},
        {"", "apple music",       "Apple Music"},
        {"", "apple icloud",      "Apple iCloud"},
        {"", "microsoft 365",     "Microsoft 365"},
        {"", "office 365",        "Microsoft 365"},
        {"", "swiggy instamart",  "Swiggy Instamart"},
        {"", "instamart",         "Swiggy Instamart"}, // before "swiggy" single-word
        {"", "vi recharge",       "Vi"},
        {"US", "disney plus",     "Disney+"},
        {"", "google one",        "Google One"},
        {"", "google storage",    "Google One"},
        {"AE", "emirates nbd",    "Emirates NBD"},
        {"IN", "jio mart",        "JioMart"},
        {"IN", "jiomart",         "JioMart"},
        {"IN", "hdfc life",       "HDFC Life"},
        {"IN", "icici prudential","ICICI Prudential"},
        {"IN", "icici lombard",   "ICICI Lombard"},
        {"IN", "sbi card",        "SBI Card"},
        {"IN", "sbi life",        "SBI Life"},
        {"IN", "tata 1mg",        "Tata 1mg"},
        {"IN", "1mg",             "Tata 1mg"},
        {"IN", "tata play",       "Tata Play"},
        {"IN", "tata sky",        "Tata Play"},
        {"IN", "tata cliq",       "Tata CLiQ"},
        {"IN", "air india",       "Air India"},
        {"IN", "delhi metro",     "Delhi Metro"},
        {"IN", "dmrc",            "Delhi Metro"},
        {"IN", "namma metro",     "Namma Metro"},
        {"IN", "bmrcl",           "Namma Metro"},
        {"IN", "cafe coffee day", "Cafe Coffee Day"},
        {"IN", "barbeque nation", "Barbeque Nation"},
        {"IN", "behrouz biryani", "Behrouz Biryani"},
        {"IN", "saravana bhavan", "Saravana Bhavan"},
        {"IN", "reliance fresh",  "Reliance Fresh"},
        {"IN", "reliance digital","Reliance Digital"},
        {"IN", "more retail",     "More"},
        {"IN", "natures basket",  "Nature's Basket"},
        {"IN", "avenue supermarts","DMart"},
        {"IN", "shoppers stop",   "Shoppers Stop"},
        {"IN", "apollo pharmacy", "Apollo Pharmacy"},
        {"IN", "manipal hospitals","Manipal Hospitals"},
        {"IN", "lal pathlabs",    "Dr Lal PathLabs"},
        {"IN", "act fibernet",    "ACT Fibernet"},
        {"IN", "mahanagar gas",   "Mahanagar Gas"},
        {"IN", "indian oil",      "Indian Oil"},
        {"IN", "taj hotels",      "Taj Hotels"},
        {"IN", "delhi public school", "Delhi Public School"},
        {"IN", "naturals salon",  "Naturals Salon"},
        {"IN", "passport seva",   "Passport Seva"},
        {"SA", "saudi electricity","Saudi Electricity"},
        {"SA", "national water",  "National Water Company"},
        {"SA", "riyadh metro",    "Riyadh Metro"},
        {"SA", "al baik",         "Al Baik"},
        {"SA", "dr cafe",         "Dr. Cafe"},
        {"SA", "panda retail",    "Panda"},
        {"SA", "panda hypermarket","Panda"},
        {"SA", "tamimi markets",  "Tamimi Markets"},
        {"SA", "nana direct",     "Nana"},
        {"SA", "united electronics","eXtra"},
        {"SA", "muvi cinemas",    "Muvi Cinemas"},
        {"SA", "al dawaa",        "Al Dawaa"},
        {"SA", "sulaiman al habib","Dr Sulaiman Al Habib"},
        {"SA", "bupa arabia",     "Bupa Arabia"},
        {"SA", "kingdom schools", "Kingdom Schools"},
        {"SA", "king saud university","King Saud University"},
        {"SA", "fitness time",    "Fitness Time"},
        {"AE", "dubai taxi",      "Dubai Taxi"},
        {"AE", "dubai police",    "Dubai Police"},
        {"AE", "dubai parks",     "Dubai Parks"},
        {"AE", "dubai cares",     "Dubai Cares"},
        {"AE", "rta nol",         "RTA Nol"},
        {"AE", "nol card",        "RTA Nol"},
        {"AE", "rta parking",     "RTA Parking"},
        {"AE", "tim hortons",     "Tim Hortons"},
        {"AE", "shake shack",     "Shake Shack"},
        {"AE", "operation falafel","Operation Falafel"},
        {"AE", "lulu hypermarket","Lulu Hypermarket"},
        {"AE", "union coop",      "Union Coop"},
        {"AE", "sharaf dg",       "Sharaf DG"},
        {"AE", "ace hardware",    "ACE"},
        {"AE", "home centre",     "Home Centre"},
        {"AE", "du telecom",      "du"},
        {"AE", "empower cooling", "Empower"},
        {"AE", "vox cinemas",     "VOX Cinemas"},
        {"AE", "reel cinemas",    "Reel Cinemas"},
        {"AE", "osn plus",        "OSN+"},
        {"AE", "aster pharmacy",  "Aster Pharmacy"},
        {"AE", "life pharmacy",   "Life Pharmacy"},
        {"AE", "nmc healthcare",  "NMC"},
        {"AE", "gems education",  "GEMS Education"},
        {"AE", "air arabia",      "Air Arabia"},
        {"AE", "fitness first",   "Fitness First"},
        {"", "booking com",       "Booking.com"},
        {"US", "american express","American Express"},
        {"US", "american airlines","American Airlines"},
        {"US", "united airlines", "United"},
        {"US", "delta air lines", "Delta"},
        {"US", "delta airlines",  "Delta"},
        {"US", "southwest airlines","Southwest"},
        {"US", "whole foods",     "Whole Foods"},
        {"US", "trader joes",     "Trader Joe's"},
        {"US", "home depot",      "The Home Depot"},
        {"US", "best buy",        "Best Buy"},
        {"US", "new york times",  "The New York Times"},
        {"US", "nytimes",         "The New York Times"},
        {"US", "planet fitness",  "Planet Fitness"},
        {"US", "panera bread",    "Panera Bread"},
        {"US", "blue bottle",     "Blue Bottle Coffee"},
        {"US", "burger king",     "Burger King"},
        {"US", "pizza hut",       "Pizza Hut"},
        {"US", "taco bell",       "Taco Bell"},
        {"US", "con edison",      "Con Edison"},
        {"US", "duke energy",     "Duke Energy"},
        {"US", "pg&e",            "PG&E"},
        {"US", "at&t",            "AT&T"},
        {"US", "t-mobile",        "T-Mobile"},
        {"US", "state farm",      "State Farm"},
        {"US", "progressive insurance","Progressive"},
        {"US", "progressive ins", "Progressive"},
        {"US", "kaiser permanente","Kaiser Permanente"},
        {"US", "quest diagnostics","Quest Diagnostics"},
        {"US", "one medical",     "One Medical"},
        {"US", "sallie mae",      "Sallie Mae"},
        {"US", "wells fargo",     "Wells Fargo"},
        {"US", "barnes noble",    "Barnes & Noble"},
        {"US", "e-zpass",         "E-ZPass"},
        {"US", "mta nyct",        "MTA"},
        {"US", "amc theatres",    "AMC Theatres"},
        {"US", "amc theaters",    "AMC Theatres"},
        {"US", "steam games",     "Steam"},
        {"US", "playstation network","PlayStation Network"},
        {"US", "great clips",     "Great Clips"},
        {"US", "shell oil",       "Shell"},

        {"", "netflix",           "Netflix"},
        {"", "spotify",           "Spotify"},
        {"", "disney",            "Disney+ Hotstar"},
        {"", "hotstar",           "Disney+ Hotstar"},
        {"", "icloud",            "Apple iCloud"},
        {"", "adobe",             "Adobe"},
        {"", "github",            "GitHub"},
        {"", "openai",            "OpenAI"},
        {"", "chatgpt",           "OpenAI"},
        {"", "anthropic",         "Anthropic"},
        {"", "claude",            "Anthropic"},
        {"", "dropbox",           "Dropbox"},
        {"", "notion",            "Notion"},
        {"", "linkedin",          "LinkedIn"},
        {"", "canva",             "Canva"},
        {"", "figma",             "Figma"},

        {"", "zomato",            "Zomato"},
        {"", "swiggy",            "Swiggy"},
        {"", "blinkit",           "Blinkit"},
        {"", "bigbasket",         "BigBasket"},
        {"", "dunzo",             "Dunzo"},
        {"", "zepto",             "Zepto"},
        {"", "amazon",            "Amazon"},
        {"", "amzn",              "Amazon"},
        {"", "flipkart",          "Flipkart"},
        {"", "meesho",            "Meesho"},
        {"", "myntra",            "Myntra"},
        {"", "nykaa",             "Nykaa"},
        {"", "uber",              "Uber"},
        {"", "ola",               "Ola"},
        {"", "rapido",            "Rapido"},
        {"", "irctc",             "IRCTC"},
        {"", "makemytrip",        "MakeMyTrip"},
        {"", "goibibo",           "Goibibo"},

        {"", "starbucks",         "Starbucks"},
        {"", "mcdonalds",         "McDonald's"},
        {"", "dominos",           "Domino's"},
        {"", "kfc",               "KFC"},
        {"", "carrefour",         "Carrefour"},
        {"", "ikea",              "IKEA"},
        {"", "coursera",          "Coursera"},
        {"", "udemy",             "Udemy"},
        {"", "marriott",          "Marriott"},
        {"", "hilton",            "Hilton"},
        {"", "airbnb",            "Airbnb"},
        {"", "expedia",           "Expedia"},
        {"", "tabby",             "Tabby"},
        {"", "careem",            "Careem"},
        {"", "noon",              "Noon"},
        {"", "namshi",            "Namshi"},
        {"", "boots",             "Boots"},
        {"IN", "haldirams",       "Haldiram's"},
        {"IN", "chaayos",         "Chaayos"},
        {"IN", "dmart",           "DMart"},
        {"IN", "spencers",        "Spencer's"},
        {"IN", "ajio",            "Ajio"},
        {"IN", "decathlon",       "Decathlon"},
        {"IN", "croma",           "Croma"},
        {"IN", "westside",        "Westside"},
        {"IN", "hpcl",            "HPCL"},
        {"IN", "bpcl",            "BPCL"},
        {"IN", "iocl",            "Indian Oil"},
        {"IN", "fastag",          "FASTag"},
        {"IN", "bookmyshow",      "BookMyShow"},
        {"IN", "sonyliv",         "SonyLIV"},
        {"IN", "zee5",            "ZEE5"},
        {"IN", "jiocinema",       "JioCinema"},
        {"IN", "pvr",             "PVR"},
        {"IN", "inox",            "INOX"},
        {"IN", "medplus",         "MedPlus"},
        {"IN", "pharmeasy",       "PharmEasy"},
        {"IN", "practo",          "Practo"},
        {"IN", "lic",             "LIC"},
        {"IN", "zerodha",         "Zerodha"},
        {"IN", "groww",           "Groww"},
        {"IN", "cred",            "CRED"},
        {"IN", "byjus",           "Byju's"},
        {"IN", "unacademy",       "Unacademy"},
        {"IN", "fiitjee",         "FIITJEE"},
        {"IN", "indigo",          "IndiGo"},
        {"IN", "interglobe",      "IndiGo"},
        {"IN", "oyo",             "OYO"},
        {"IN", "redbus",          "redBus"},
        {"IN", "ihcl",            "Taj Hotels"},
        {"IN", "cleartrip",       "Cleartrip"},
        {"IN", "giveindia",       "GiveIndia"},
        {"SA", "albaik",          "Al Baik"},
        {"SA", "kudu",            "Kudu"},
        {"SA", "hungerstation",   "HungerStation"},
        {"SA", "jahez",           "Jahez"},
        {"SA", "herfy",           "Herfy"},
        {"SA", "barns",           "Barn's"},
        {"SA", "danube",          "Danube"},
        {"SA", "tamimi",          "Tamimi Markets"},
        {"SA", "othaim",          "Othaim"},
        {"SA", "jarir",           "Jarir Bookstore"},
        {"SA", "centrepoint",     "Centrepoint"},
        {"SA", "aldrees",         "Aldrees"},
        {"SA", "petromin",        "Petromin"},
        {"SA", "sasco",           "SASCO"},
        {"SA", "mawgif",          "Mawgif"},
        {"SA", "stc",             "STC"},
        {"SA", "mobily",          "Mobily"},
        {"SA", "zain",            "Zain"},
        {"SA", "shahid",          "Shahid"},
        {"SA", "webook",          "Webook"},
        {"SA", "anghami",         "Anghami"},
        {"SA", "nahdi",           "Nahdi Pharmacy"},
        {"SA", "mouwasat",        "Mouwasat"},
        {"SA", "tawuniya",        "Tawuniya"},
        {"SA", "tamara",          "Tamara"},
        {"SA", "saudia",          "Saudia"},
        {"SA", "flynas",          "flynas"},
        {"SA", "flyadeal",        "flyadeal"},
        {"SA", "almosafer",       "Almosafer"},
        {"SA", "haramain",        "Haramain Railway"},
        {"SA", "absher",          "Absher"},
        {"SA", "ehsan",           "Ehsan"},
        {"AE", "talabat",         "Talabat"},
        {"AE", "deliveroo",       "Deliveroo"},
        {"AE", "lulu",            "Lulu Hypermarket"},
        {"AE", "spinneys",        "Spinneys"},
        {"AE", "waitrose",        "Waitrose"},
        {"AE", "choithrams",      "Choithrams"},
        {"AE", "kibsons",         "Kibsons"},
        {"AE", "instashop",       "Instashop"},
        {"AE", "salik",           "Salik"},
        {"AE", "enoc",            "ENOC"},
        {"AE", "adnoc",           "ADNOC"},
        {"AE", "emarat",          "Emarat"},
        {"AE", "dewa",            "DEWA"},
        {"AE", "taqa",            "TAQA"},
        {"AE", "addc",            "TAQA"},
        {"AE", "sewa",            "SEWA"},
        {"AE", "etisalat",        "e&"},
        {"AE", "platinumlist",    "Platinumlist"},
        {"AE", "mediclinic",      "Mediclinic"},
        {"AE", "nmc",             "NMC"},
        {"AE", "axa",             "AXA"},
        {"AE", "sarwa",           "Sarwa"},
        {"AE", "kumon",           "Kumon"},
        {"AE", "emirates",        "Emirates"},
        {"AE", "flydubai",        "flydubai"},
        {"AE", "etihad",          "Etihad"},
        {"AE", "rotana",          "Rotana"},
        {"US", "chipotle",        "Chipotle"},
        {"US", "doordash",        "DoorDash"},
        {"US", "grubhub",         "Grubhub"},
        {"US", "panera",          "Panera Bread"},
        {"US", "sweetgreen",      "Sweetgreen"},
        {"US", "dunkin",          "Dunkin'"},
        {"US", "kroger",          "Kroger"},
        {"US", "safeway",         "Safeway"},
        {"US", "costco",          "Costco"},
        {"US", "instacart",       "Instacart"},
        {"US", "aldi",            "Aldi"},
        {"US", "publix",          "Publix"},
        {"US", "target",          "Target"},
        {"US", "walmart",         "Walmart"},
        {"US", "wal-mart",        "Walmart"},
        {"US", "etsy",            "Etsy"},
        {"US", "ebay",            "eBay"},
        {"US", "nike",            "Nike"},
        {"US", "macys",           "Macy's"},
        {"US", "lyft",            "Lyft"},
        {"US", "shell",           "Shell"},
        {"US", "chevron",         "Chevron"},
        {"US", "exxonmobil",      "ExxonMobil"},
        {"US", "exxon",           "ExxonMobil"},
        {"US", "mobil",           "ExxonMobil"},
        {"US", "bp",              "BP"},
        {"US", "mta",             "MTA"},
        {"US", "ezpass",          "E-ZPass"},
        {"US", "spothero",        "SpotHero"},
        {"US", "amtrak",          "Amtrak"},
        {"US", "verizon",         "Verizon"},
        {"US", "xfinity",         "Xfinity"},
        {"US", "comcast",         "Xfinity"},
        {"US", "hulu",            "Hulu"},
        {"US", "ticketmaster",    "Ticketmaster"},
        {"US", "steampowered",    "Steam"},
        {"US", "playstation",     "PlayStation Network"},
        {"US", "cvs",             "CVS"},
        {"US", "walgreens",       "Walgreens"},
        {"US", "kaiser",          "Kaiser Permanente"},
        {"US", "labcorp",         "Labcorp"},
        {"US", "geico",           "GEICO"},
        {"US", "chase",           "Chase"},
        {"US", "amex",            "American Express"},
        {"US", "robinhood",       "Robinhood"},
        {"US", "vanguard",        "Vanguard"},
        {"US", "chegg",           "Chegg"},
        {"US", "nyu",             "NYU"},
        {"US", "goodwill",        "Goodwill"},
        {"US", "usps",            "USPS"},
        {"US", "dmv",             "DMV"},

        {"", "tata power",        "Tata Power"},
        {"", "adani electric",    "Adani Electricity"},
        {"", "torrent power",     "Torrent Power"},
        {"", "jio",               "Jio"},
        {"", "airtel",            "Airtel"},
        {"", "vodafone",          "Vi"},
        {"", "bsnl",              "BSNL"},
        {"", "bescom",            "BESCOM"},
        {"IN", "bwssb",           "BWSSB"},
        {"IN", "hathway",         "Hathway"},

        // parent brands, after their specific products above
        {"", "apple",             "Apple"},
        {"", "google",            "Google"},
        {"", "microsoft",         "Microsoft"},
        {"US", "coca cola",       "Coca-Cola"},

        // before bank names so "EMI ICICI" resolves to EMI, not ICICI Bank
        {"", "emi",               "EMI"},
        {"", "home loan",         "Loan EMI"},
        {"", "car loan",          "Loan EMI"},
        {"", "personal loan",     "Loan EMI"},
        {"", "loan",              "Loan EMI"},
        {"", "rent",              "Rent"},
        {"", "electricity",       "Electricity"},
        {"", "electric",          "Electricity"},
        {"", "gas bill",          "Gas"},
        {"", "water bill",        "Water"},
        {"", "broadband",         "Broadband"},
        {"", "internet",          "Broadband"},

        {"", "yes bank",          "Yes Bank"},
        {"", "idfc first",        "IDFC FIRST Bank"},
        {"", "idfc",              "IDFC FIRST Bank"},
        {"", "hdfc",              "HDFC Bank"},
        {"", "icici",             "ICICI Bank"},
        {"", "sbi",               "SBI"},
        {"", "axis",              "Axis Bank"},
        {"", "kotak",             "Kotak Bank"},
        {"", "rbl",               "RBL Bank"},
        {"IN", "bajaj finance",   "Bajaj Finance"},
        {"IN", "bajaj finserv",   "Bajaj Finance"},
        {"SA", "al rajhi",        "Al Rajhi Bank"},
        {"SA", "saudi national bank","SNB"},
        {"SA", "snb",             "SNB"},
        {"SA", "riyad bank",      "Riyad Bank"},
        {"AE", "adcb",            "ADCB"},
        {"AE", "first abu dhabi bank","FAB"},
        {"AE", "fab",             "FAB"},
        {"AE", "mashreq",         "Mashreq"},

        {"", "cure.fit",          "cure.fit"},
        {"", "curefit",           "cure.fit"},
        {"", "cult.fit",          "cure.fit"},
        {"", "gold gym",          "Gold's Gym"},
        {"", "anytime fit",       "Anytime Fitness"},
    };

    private static final List<AliasIndex.Entry> BUILT_IN_ENTRIES = entries();
    private static final AliasIndex BUILT_IN_INDEX = new AliasIndex(BUILT_IN_ENTRIES);

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

    private static final Pattern RAIL_WORDS = Pattern.compile("\\b(upi|imps|neft|rtgs|ach|paytm|gpay|phonepe|bhim)\\b");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static List<AliasIndex.Entry> builtInAliases() {
        return BUILT_IN_ENTRIES;
    }

    public static AliasIndex builtInIndex() {
        return BUILT_IN_INDEX;
    }

    /** The cleaned, lower-case token text that aliases and keys are computed from. */
    public String matchText(String raw) {
        String s = MerchantText.matchText(MerchantText.clean(raw));
        s = RAIL_WORDS.matcher(s).replaceAll(" ");
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    // Never returns null; returns Normalized.empty() for blank input.
    public Normalized normalize(String raw) {
        if (raw == null || raw.isBlank()) return Normalized.empty();

        AliasIndex.Entry stored = BUILT_IN_INDEX.canonical(raw);
        if (stored != null) return new Normalized(stored.canonicalKey(), stored.canonicalName());

        String s = matchText(raw);
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
        AliasIndex.Entry entry = BUILT_IN_INDEX.match(text);
        return entry == null ? null : new Normalized(entry.canonicalKey(), entry.canonicalName());
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

    private static List<AliasIndex.Entry> entries() {
        List<AliasIndex.Entry> list = new ArrayList<>(BUILT_IN.length);
        for (int i = 0; i < BUILT_IN.length; i++) {
            String[] row = BUILT_IN[i];
            list.add(AliasIndex.entry(row[0].isEmpty() ? null : row[0], row[1], row[2], (i + 1) * 10));
        }
        return List.copyOf(list);
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
