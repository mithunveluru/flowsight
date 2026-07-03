package com.flowsight.analytics;

import com.flowsight.entity.TransactionCategory;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

// Keyword-to-category classifier; returns (category, confidence).
@Service
public class CategorizationService {

    private static final Map<TransactionCategory, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(TransactionCategory.FOOD_DINING, List.of(
            "zomato", "swiggy", "dunzo", "restaurant", "cafe", "coffee", "bakery",
            "pizza", "burger", "dominos", "mcdonald", "kfc", "subway", "biryani",
            "dhaba", "hotel", "food", "eat", "canteen", "mess", "tiffin"
        ));
        KEYWORDS.put(TransactionCategory.GROCERIES, List.of(
            "grofer", "bigbasket", "blinkit", "zepto", "jiomart", "grocery",
            "supermarket", "dmart", "more", "reliance fresh", "fresh", "vegetable",
            "kirana", "provisions", "departmental", "nature basket"
        ));
        KEYWORDS.put(TransactionCategory.SHOPPING, List.of(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq",
            "shopping", "mall", "store", "retail", "bazaar", "mart", "shop",
            "fashion", "clothing", "apparel", "electronics", "gadget", "decathlon"
        ));
        KEYWORDS.put(TransactionCategory.TRANSPORTATION, List.of(
            "uber", "ola", "rapido", "blumart", "petrol", "diesel", "fuel",
            "hpcl", "bpcl", "iocl", "cng", "auto", "taxi", "cab",
            "metro", "irctc", "toll", "parking", "fastag", "e-way"
        ));
        KEYWORDS.put(TransactionCategory.UTILITIES, List.of(
            "electricity", "bses", "bescom", "tata power", "msedcl", "adani electricity",
            "water", "municipal", "bwssb", "broadband", "internet", "wifi",
            "airtel", "jio", "vi", "vodafone", "idea", "bsnl", "mtnl",
            "postpaid", "prepaid", "recharge", "dth", "tata sky", "dish tv"
        ));
        KEYWORDS.put(TransactionCategory.ENTERTAINMENT, List.of(
            "netflix", "hotstar", "disney", "amazon prime", "sonyliv", "zee5",
            "spotify", "gaana", "youtube premium", "apple music",
            "pvr", "inox", "cinepolis", "movie", "theatre", "cinema",
            "steam", "gaming", "playstation", "xbox", "game"
        ));
        KEYWORDS.put(TransactionCategory.HEALTHCARE, List.of(
            "hospital", "clinic", "pharmacy", "medical", "medicine", "chemist",
            "apollo", "manipal", "fortis", "max hospital", "aiims",
            "doctor", "lab", "diagnostic", "pathology", "dental", "optical",
            "medplus", "1mg", "pharmeasy", "netmeds", "practo"
        ));
        KEYWORDS.put(TransactionCategory.FINANCE, List.of(
            "emi", "loan", "repayment", "insurance", "lic", "premium",
            "sip", "mutual fund", "zerodha", "groww", "demat", "brokerage",
            "bank charges", "service charge", "processing fee", "annual fee",
            "credit card", "nps", "ppf", "fd", "rd"
        ));
        KEYWORDS.put(TransactionCategory.EDUCATION, List.of(
            "udemy", "coursera", "unacademy", "byjus", "vedantu", "khan academy",
            "school", "college", "university", "tuition", "coaching",
            "books", "educational", "course", "skill", "certification"
        ));
        KEYWORDS.put(TransactionCategory.TRAVEL, List.of(
            "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo",
            "airbnb", "oyo", "treebo", "fabhotels",
            "flight", "airways", "airline", "indigo", "air india", "spicejet",
            "railways", "irctc train", "bus booking", "redbus"
        ));
        KEYWORDS.put(TransactionCategory.SUBSCRIPTIONS, List.of(
            "subscription", "plan renewal", "auto renewal", "auto debit",
            "standing instruction", "si debit", "recurring", "monthly plan"
        ));
        KEYWORDS.put(TransactionCategory.INCOME, List.of(
            "salary", "stipend", "freelance", "payment received", "credited",
            "cashback", "refund", "dividend", "interest credited", "bonus",
            "incentive", "commission received", "reimbursement"
        ));
        KEYWORDS.put(TransactionCategory.TRANSFER, List.of(
            "neft", "rtgs", "imps", "upi transfer", "sent to",
            "transfer to", "transfer from", "mobile banking transfer",
            "wallet transfer", "paytm", "phonepe", "gpay", "google pay"
        ));
    }

    public CategorizationResult categorize(String description, String merchant, BigDecimal amount) {
        String haystack = buildHaystack(description, merchant);
        Map<TransactionCategory, Double> scores = new HashMap<>();

        for (Map.Entry<TransactionCategory, List<String>> entry : KEYWORDS.entrySet()) {
            TransactionCategory category = entry.getKey();
            List<String> keywords = entry.getValue();

            long matchCount = keywords.stream()
                .filter(haystack::contains)
                .count();

            if (matchCount > 0) {
                // longer (brand-specific) keywords score higher
                double base = 0.45;
                double perMatch = 0.12;
                double bonus = keywords.stream()
                    .filter(haystack::contains)
                    .mapToDouble(k -> k.length() > 6 ? 0.08 : 0.0)
                    .sum();
                double score = Math.min(base + (matchCount * perMatch) + bonus, 0.95);
                scores.put(category, score);
            }
        }

        if (scores.isEmpty()) {
            return new CategorizationResult(TransactionCategory.UNCATEGORIZED, 0.25);
        }

        return scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> new CategorizationResult(e.getKey(), e.getValue()))
            .orElse(new CategorizationResult(TransactionCategory.UNCATEGORIZED, 0.25));
    }

    private String buildHaystack(String description, String merchant) {
        StringBuilder sb = new StringBuilder();
        if (description != null) sb.append(description.toLowerCase()).append(" ");
        if (merchant != null) sb.append(merchant.toLowerCase());
        return sb.toString();
    }

    @Value
    public static class CategorizationResult {
        TransactionCategory category;
        double confidence;
    }
}
