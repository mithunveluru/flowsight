package com.flowsight.assistant;

import com.flowsight.llm.FactSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.flowsight.assistant.AssistantService.date;
import static com.flowsight.assistant.AssistantService.money;

/** Deterministic answers built only from one tool result's facts. */
final class RuleAnswers {

    private static final int LIST_ROWS = 3;

    private RuleAnswers() {
    }

    static String render(IntentMatcher.Intent intent, ToolResult r) {
        FactSet f = r.facts();
        String range = "from " + date(f.get("from")) + " to " + date(f.get("to"));
        return switch (intent.type()) {
            case SPENDING_SUMMARY -> String.format(Locale.ENGLISH,
                "From %s to %s you spent %s and received %s across %s transactions.",
                date(f.get("from")), date(f.get("to")), money(f.get("total_spend")), money(f.get("total_income")),
                f.get("transaction_count"));
            case SPENDING_BY_CATEGORY -> intent.category() != null
                ? String.format(Locale.ENGLISH, "You spent %s on %s %s, %s%% of your spending.",
                    money(f.get("category_spend")), f.get("category"), range, plain(f.get("category_share_percent")))
                : list(f, "categories", "name", "spend", "Your biggest categories " + range + ": ",
                    "No spending was recorded " + range + ".");
            case TOP_MERCHANTS -> list(f, "merchants", "name", "spend", "Your top merchants " + range + ": ",
                "No merchant spending was recorded " + range + ".");
            case RECURRING -> count(f, "recurring_count") == 0
                ? "No recurring payments have been detected yet."
                : String.format(Locale.ENGLISH,
                    "You have %s recurring payments, about %s a month in total. The largest is %s at %s a month.",
                    f.get("recurring_count"), money(f.get("total_monthly")), f.get("recurring.1.name"),
                    money(f.get("recurring.1.monthly_equivalent")));
            case LEAKS -> count(f, "leak_count") == 0
                ? "No money leaks were found in your recent spending."
                : String.format(Locale.ENGLISH,
                    "FlowSight found %s possible leaks worth %s a month (%s a year). The biggest is %s at %s a month.",
                    f.get("leak_count"), money(f.get("total_monthly")), money(f.get("total_annual")),
                    f.get("leaks.1.title"), money(f.get("leaks.1.monthly")));
            case BUDGET_STATUS -> count(f, "budget_count") == 0
                ? "You have no active budgets."
                : String.format(Locale.ENGLISH,
                    "This month you have spent %s of %s budgeted across %s budgets; %s over the limit.",
                    money(f.get("total_spent")), money(f.get("total_budgeted")), f.get("budget_count"),
                    f.get("over_budget_count"));
            case GOAL_STATUS -> goals(f);
            default -> "";
        };
    }

    private static String list(FactSet f, String prefix, String nameKey, String amountKey, String lead, String empty) {
        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= LIST_ROWS && f.get(prefix + "." + i + "." + nameKey) != null; i++) {
            parts.add(f.get(prefix + "." + i + "." + nameKey) + " " + money(f.get(prefix + "." + i + "." + amountKey)));
        }
        return parts.isEmpty() ? empty : lead + String.join(", ", parts) + ".";
    }

    private static String goals(FactSet f) {
        if (count(f, "goal_count") == 0) return "You have no savings goals yet.";
        List<String> parts = new ArrayList<>();
        for (int i = 1; i <= LIST_ROWS && f.get("goals." + i + ".name") != null; i++) {
            String p = "goals." + i + ".";
            parts.add(String.format(Locale.ENGLISH, "'%s': %s of %s saved, target date %s",
                f.get(p + "name"), money(f.get(p + "saved")), money(f.get(p + "target")), date(f.get(p + "target_date"))));
        }
        return String.join("; ", parts) + ".";
    }

    private static long count(FactSet f, String key) {
        return f.get(key) instanceof BigDecimal bd ? bd.longValue() : 0;
    }

    private static String plain(Object v) {
        return v instanceof BigDecimal bd ? bd.toPlainString() : "0";
    }
}
