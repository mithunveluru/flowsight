package com.flowsight.assistant;

import com.flowsight.entity.TransactionCategory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Keyword intent matching for the no-LLM path. It answers a fixed subset of questions and
 * says so for the rest; write requests and questions about other people are refused.
 */
public final class IntentMatcher {

    public enum Type {
        SPENDING_SUMMARY, SPENDING_BY_CATEGORY, TOP_MERCHANTS, RECURRING, LEAKS, BUDGET_STATUS, GOAL_STATUS,
        WRITE_REQUEST, OTHER_USERS, UNSUPPORTED
    }

    public record Intent(Type type, Period period, TransactionCategory category) {
    }

    private static final Pattern WRITE = Pattern.compile(
        "^(please\\s+)?((can|could|would|will)\\s+you\\s+(please\\s+)?)?"
        + "(cancel|delete|remove|create|add|set( up)?|update|change|transfer|pay|move|edit|rename|mark|send|"
        + "categori[sz]e|recategori[sz]e|unsubscribe|dismiss|confirm|make)\\b");
    private static final Pattern OTHER_USERS = Pattern.compile(
        "\\b(other (users?|people|customers|accounts)|another (user|person|account)|someone else|"
        + "everyone|all users|user id|userid|my (friend|wife|husband|partner|brother|sister)'?s?)\\b");
    private static final Pattern GOAL = Pattern.compile("\\b(goals?|saving (up )?for|savings target)\\b");
    private static final Pattern BUDGET = Pattern.compile("\\b(budgets?|over (my )?limit|within (my )?limit)\\b");
    private static final Pattern LEAK = Pattern.compile(
        "\\b(leaks?|leaking|wast(e|ed|ing)|where (can|could) i save|save money|cut back|bank fees|fees)\\b");
    private static final Pattern RECURRING = Pattern.compile(
        "\\b(recurring|subscriptions?|bills?|emis?|auto-?pay|autodebit|repeat(ing)? payments?|standing orders?)\\b");
    private static final Pattern MERCHANT = Pattern.compile(
        "\\b(merchants?|stores?|shops?|vendors?|where (do|did) i (spend|shop) (the )?most|who (do|did) i pay)\\b");
    private static final Pattern CATEGORY_WORD = Pattern.compile("\\b(categor(y|ies)|breakdown|split|by type)\\b");
    private static final Pattern SPEND_WORD = Pattern.compile("\\b(spend|spent|spending|cost|paid|pay for|expenses?)\\b");
    private static final Pattern SUMMARY = Pattern.compile(
        "\\b(how much (did|have) i (spend|spent)|total (spend|spending|expenses)|income|earn(ed|ings)?|"
        + "cash ?flow|net|overview|summary|spend(ing)? (this|last)|how am i doing)\\b");

    private static final Map<Pattern, TransactionCategory> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put(Pattern.compile("\\b(food|dining|restaurants?|eating out|takeaway|delivery|zomato|swiggy|cafes?|coffee)\\b"), TransactionCategory.FOOD_DINING);
        CATEGORIES.put(Pattern.compile("\\b(groceries|grocery|supermarket)\\b"), TransactionCategory.GROCERIES);
        CATEGORIES.put(Pattern.compile("\\b(shopping|clothes|amazon|flipkart)\\b"), TransactionCategory.SHOPPING);
        CATEGORIES.put(Pattern.compile("\\b(transport(ation)?|uber|ola|cabs?|taxi|fuel|petrol|commute|metro)\\b"), TransactionCategory.TRANSPORTATION);
        CATEGORIES.put(Pattern.compile("\\b(utilities|electricity|water bill|internet|broadband|phone bill|mobile bill)\\b"), TransactionCategory.UTILITIES);
        CATEGORIES.put(Pattern.compile("\\b(entertainment|movies?|cinema|games?|concerts?)\\b"), TransactionCategory.ENTERTAINMENT);
        CATEGORIES.put(Pattern.compile("\\b(health(care)?|medical|doctors?|pharmacy|medicines?|hospital)\\b"), TransactionCategory.HEALTHCARE);
        CATEGORIES.put(Pattern.compile("\\b(education|courses?|tuition|school|college)\\b"), TransactionCategory.EDUCATION);
        CATEGORIES.put(Pattern.compile("\\b(travel|flights?|hotels?|trips?|vacation|holiday)\\b"), TransactionCategory.TRAVEL);
        CATEGORIES.put(Pattern.compile("\\b(subscriptions? (category|spend(ing)?)|on subscriptions)\\b"), TransactionCategory.SUBSCRIPTIONS);
    }

    private IntentMatcher() {
    }

    public static Intent match(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        Period period = period(q);
        if (q.isEmpty()) return new Intent(Type.UNSUPPORTED, period, null);
        if (WRITE.matcher(q).find()) return new Intent(Type.WRITE_REQUEST, period, null);
        if (OTHER_USERS.matcher(q).find()) return new Intent(Type.OTHER_USERS, period, null);

        TransactionCategory category = category(q);
        boolean spendQuestion = SPEND_WORD.matcher(q).find() || q.startsWith("how much");
        if (GOAL.matcher(q).find()) return new Intent(Type.GOAL_STATUS, period, null);
        if (BUDGET.matcher(q).find()) return new Intent(Type.BUDGET_STATUS, period, null);
        if (category != null && spendQuestion) return new Intent(Type.SPENDING_BY_CATEGORY, period, category);
        if (LEAK.matcher(q).find()) return new Intent(Type.LEAKS, period, null);
        if (RECURRING.matcher(q).find()) return new Intent(Type.RECURRING, period, null);
        if (MERCHANT.matcher(q).find()) return new Intent(Type.TOP_MERCHANTS, period, null);
        if (CATEGORY_WORD.matcher(q).find() || category != null) {
            return new Intent(Type.SPENDING_BY_CATEGORY, period, category);
        }
        if (SUMMARY.matcher(q).find() || spendQuestion) return new Intent(Type.SPENDING_SUMMARY, period, null);
        return new Intent(Type.UNSUPPORTED, period, null);
    }

    static Period period(String q) {
        if (q.matches(".*\\b(last|previous|past) month\\b.*")) return Period.LAST_MONTH;
        if (q.matches(".*\\b(last|past) (3|three) months\\b.*|.*\\b(last|past) (90|ninety) days\\b.*|.*\\bquarter\\b.*")) {
            return Period.LAST_90_DAYS;
        }
        if (q.matches(".*\\b(last|past) (30|thirty) days\\b.*|.*\\b(last|past) (few )?weeks?\\b.*")) return Period.LAST_30_DAYS;
        if (q.matches(".*\\b(this|the) year\\b.*|.*\\bso far this year\\b.*|.*\\bytd\\b.*|.*\\byear to date\\b.*")) {
            return Period.THIS_YEAR;
        }
        return Period.THIS_MONTH;
    }

    private static TransactionCategory category(String q) {
        for (Map.Entry<Pattern, TransactionCategory> e : CATEGORIES.entrySet()) {
            if (e.getKey().matcher(q).find()) return e.getValue();
        }
        return null;
    }
}
