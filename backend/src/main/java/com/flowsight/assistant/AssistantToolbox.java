package com.flowsight.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowsight.analytics.AnalyticsService;
import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.analytics.MerchantSummary;
import com.flowsight.dto.budget.BudgetResponse;
import com.flowsight.dto.budget.BudgetSummaryResponse;
import com.flowsight.dto.goal.GoalResponse;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.ToolSpec;
import com.flowsight.service.BudgetService;
import com.flowsight.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The fixed, read-only tools the assistant may use. Every tool takes the authenticated user's
 * id from the caller, never from the model; the model can only choose a period, a category or
 * a result count. Results are aggregates already shown elsewhere in the app, never raw rows.
 */
@Component
@RequiredArgsConstructor
public class AssistantToolbox {

    public static final String SPENDING_SUMMARY = "spending_summary";
    public static final String SPENDING_BY_CATEGORY = "spending_by_category";
    public static final String TOP_MERCHANTS = "top_merchants";
    public static final String RECURRING_PAYMENTS = "recurring_payments";
    public static final String LEAKS = "leaks";
    public static final String BUDGET_STATUS = "budget_status";
    public static final String GOAL_STATUS = "goal_status";

    private static final int MAX_ROWS = 8;

    private final AnalyticsService          analytics;
    private final RecurringDetectionService recurring;
    private final LeakDetectionService      leaks;
    private final BudgetService             budgets;
    private final GoalService               goals;

    public List<ToolSpec> specs() {
        Map<String, Object> period = Map.of("type", "string",
            "enum", Arrays.stream(Period.values()).map(Enum::name).toList(),
            "description", "Date range; defaults to THIS_MONTH");
        Map<String, Object> category = Map.of("type", "string",
            "enum", Arrays.stream(TransactionCategory.values()).map(Enum::name).toList());
        return List.of(
            spec(SPENDING_SUMMARY, "Total spend, income, net cash flow and transaction count for a period.",
                Map.of("period", period)),
            spec(SPENDING_BY_CATEGORY, "Spend per category for a period, or for one category.",
                Map.of("period", period, "category", category)),
            spec(TOP_MERCHANTS, "Merchants with the highest spend in a period.",
                Map.of("period", period, "limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_ROWS))),
            spec(RECURRING_PAYMENTS, "Detected recurring payments and subscriptions with monthly cost.", Map.of()),
            spec(LEAKS, "Detected money leaks (duplicate subscriptions, price creep, small habits, fees).", Map.of()),
            spec(BUDGET_STATUS, "This month's budgets: limit, spent, remaining and status.", Map.of()),
            spec(GOAL_STATUS, "Savings goals: target, saved so far, target date and pace.", Map.of()));
    }

    public boolean has(String tool) {
        return specs().stream().anyMatch(s -> s.name().equals(tool));
    }

    public ToolResult run(String tool, UUID userId, JsonNode args, String resultId, LocalDate today) {
        Period period = Period.parse(text(args, "period"), Period.THIS_MONTH);
        return switch (tool) {
            case SPENDING_SUMMARY -> spendingSummary(resultId, userId, period, today);
            case SPENDING_BY_CATEGORY -> spendingByCategory(resultId, userId, period, category(text(args, "category")), today);
            case TOP_MERCHANTS -> topMerchants(resultId, userId, period, limit(args), today);
            case RECURRING_PAYMENTS -> recurringPayments(resultId, userId);
            case LEAKS -> leaks(resultId, userId);
            case BUDGET_STATUS -> budgetStatus(resultId, userId);
            case GOAL_STATUS -> goalStatus(resultId, userId);
            default -> throw new IllegalArgumentException("unknown tool");
        };
    }

    private ToolResult spendingSummary(String id, UUID userId, Period period, LocalDate today) {
        AnalyticsOverviewResponse o = analytics.getOverview(userId, period.from(today), period.to(today));
        FactSet f = periodFacts(period, today)
            .amount("total_spend", o.getTotalSpend())
            .amount("total_income", o.getTotalIncome())
            .amount("net_cashflow", o.getNetCashflow())
            .count("transaction_count", o.getTransactionCount());
        return new ToolResult(id, SPENDING_SUMMARY, "Spending summary", f);
    }

    private ToolResult spendingByCategory(String id, UUID userId, Period period, TransactionCategory only, LocalDate today) {
        AnalyticsOverviewResponse o = analytics.getOverview(userId, period.from(today), period.to(today));
        FactSet f = periodFacts(period, today).amount("total_spend", o.getTotalSpend());
        if (only != null) {
            CategoryBreakdownItem item = o.getCategoryBreakdown().stream()
                .filter(c -> only.name().equals(c.getCategory())).findFirst().orElse(null);
            f.text("category", only.getDisplayName())
                .amount("category_spend", item == null ? BigDecimal.ZERO : item.getAmount())
                .percent("category_share_percent", item == null ? 0 : item.getPercentage())
                .count("category_transaction_count", item == null ? 0 : item.getTransactionCount());
        } else {
            int i = 1;
            for (CategoryBreakdownItem c : o.getCategoryBreakdown()) {
                if (i > MAX_ROWS) break;
                f.text("categories." + i + ".name", c.getDisplayName())
                    .amount("categories." + i + ".spend", c.getAmount())
                    .percent("categories." + i + ".share_percent", c.getPercentage());
                i++;
            }
        }
        return new ToolResult(id, SPENDING_BY_CATEGORY, "Spending by category", f);
    }

    private ToolResult topMerchants(String id, UUID userId, Period period, int limit, LocalDate today) {
        AnalyticsOverviewResponse o = analytics.getOverview(userId, period.from(today), period.to(today));
        FactSet f = periodFacts(period, today);
        int i = 1;
        for (MerchantSummary m : o.getTopMerchants()) {
            if (i > limit) break;
            f.text("merchants." + i + ".name", m.getMerchant())
                .amount("merchants." + i + ".spend", m.getTotalAmount())
                .count("merchants." + i + ".transaction_count", m.getTransactionCount());
            i++;
        }
        return new ToolResult(id, TOP_MERCHANTS, "Top merchants", f);
    }

    private ToolResult recurringPayments(String id, UUID userId) {
        List<RecurringPatternResponse> patterns = recurring.getStored(userId);
        BigDecimal monthly = patterns.stream().map(RecurringPatternResponse::getMonthlyEquivalent)
            .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        FactSet f = new FactSet().count("recurring_count", patterns.size()).amount("total_monthly", monthly);
        int i = 1;
        for (RecurringPatternResponse p : patterns) {
            if (i > MAX_ROWS) break;
            f.text("recurring." + i + ".name", p.getMerchant())
                .amount("recurring." + i + ".amount", p.getEstimatedAmount())
                .text("recurring." + i + ".frequency", p.getPeriodLabel())
                .amount("recurring." + i + ".monthly_equivalent", p.getMonthlyEquivalent())
                .date("recurring." + i + ".next_expected", p.getNextExpectedDate());
            i++;
        }
        return new ToolResult(id, RECURRING_PAYMENTS, "Recurring payments", f);
    }

    private ToolResult leaks(String id, UUID userId) {
        LeakDetectionResponse r = leaks.detectLeaks(userId);
        FactSet f = new FactSet()
            .count("leak_count", r.getLeaks().size())
            .amount("total_monthly", r.getTotalMonthlyImpact())
            .amount("total_annual", r.getTotalAnnualImpact());
        int i = 1;
        for (LeakInsight l : r.getLeaks()) {
            if (i > MAX_ROWS) break;
            f.text("leaks." + i + ".title", l.getTitle())
                .amount("leaks." + i + ".monthly", l.getMonthlyImpact())
                .amount("leaks." + i + ".annual", l.getAnnualImpact())
                .count("leaks." + i + ".items", l.getAffectedItemsCount());
            i++;
        }
        return new ToolResult(id, LEAKS, "Money leaks", f);
    }

    private ToolResult budgetStatus(String id, UUID userId) {
        BudgetSummaryResponse s = budgets.getSummary(userId);
        FactSet f = new FactSet()
            .count("budget_count", s.getBudgetCount())
            .count("over_budget_count", s.getOverBudgetCount())
            .amount("total_budgeted", s.getTotalBudgeted())
            .amount("total_spent", s.getTotalSpent())
            .percent("overall_percent_used", s.getOverallPercentUsed());
        int i = 1;
        for (BudgetResponse b : s.getBudgets()) {
            if (i > MAX_ROWS) break;
            f.text("budgets." + i + ".category", b.getCategoryDisplayName() == null ? "Overall" : b.getCategoryDisplayName())
                .amount("budgets." + i + ".limit", b.getMonthlyLimit())
                .amount("budgets." + i + ".spent", b.getCurrentSpend())
                .amount("budgets." + i + ".remaining", b.getRemaining())
                .percent("budgets." + i + ".percent_used", b.getPercentUsed())
                .text("budgets." + i + ".status", b.getStatus());
            i++;
        }
        return new ToolResult(id, BUDGET_STATUS, "Budget status", f);
    }

    private ToolResult goalStatus(String id, UUID userId) {
        List<GoalResponse> list = goals.list(userId);
        FactSet f = new FactSet().count("goal_count", list.size());
        int i = 1;
        for (GoalResponse g : list) {
            if (i > MAX_ROWS) break;
            f.text("goals." + i + ".name", g.getName())
                .amount("goals." + i + ".target", g.getTargetAmount())
                .amount("goals." + i + ".saved", g.getCurrentAmount())
                .amount("goals." + i + ".remaining", g.getRemaining())
                .percent("goals." + i + ".percent_complete", g.getPercentComplete())
                .date("goals." + i + ".target_date", g.getTargetDate())
                .text("goals." + i + ".pace", g.getPaceStatus());
            i++;
        }
        return new ToolResult(id, GOAL_STATUS, "Goals", f);
    }

    private static FactSet periodFacts(Period period, LocalDate today) {
        return new FactSet()
            .text("period", period.name())
            .date("from", period.from(today))
            .date("to", period.to(today));
    }

    private static ToolSpec spec(String name, String description, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return new ToolSpec(name, description, schema);
    }

    private static String text(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) return null;
        return args.get(field).asText();
    }

    private static int limit(JsonNode args) {
        int n = args != null && args.hasNonNull("limit") ? args.get("limit").asInt(5) : 5;
        return Math.max(1, Math.min(MAX_ROWS, n));
    }

    static TransactionCategory category(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TransactionCategory.valueOf(value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
