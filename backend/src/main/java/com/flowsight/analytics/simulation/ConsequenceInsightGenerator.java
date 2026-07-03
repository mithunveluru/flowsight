package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Plain-language consequence insights grounded in the user's baseline numbers.
// Each carries a code (UI icon) and severity (POSITIVE/NEUTRAL/CAUTION/WARNING).
@Service
public class ConsequenceInsightGenerator {

    public List<ConsequenceInsight> generate(
        FinancialBaseline baseline,
        ScenarioRequest scenario,
        BigDecimal monthlyImpact,
        BigDecimal recurringDelta,
        FlexibilityScore flexibility,
        GoalImpact goalImpact
    ) {
        List<ConsequenceInsight> out = new ArrayList<>();

        out.add(buildCashFlowInsight(baseline, scenario, monthlyImpact));

        ConsequenceInsight recurring = buildRecurringInsight(baseline, recurringDelta);
        if (recurring != null) out.add(recurring);

        ConsequenceInsight category = buildCategoryInsight(baseline, scenario);
        if (category != null) out.add(category);

        if (goalImpact != null) {
            out.add(ConsequenceInsight.builder()
                .code("GOAL_DELAY")
                .severity("WARNING")
                .title("Goal completion delayed")
                .description(goalImpact.getDescription())
                .build());
        }

        if (!flexibility.getCurrentTier().equals(flexibility.getProjectedTier())) {
            out.add(buildFlexibilityShiftInsight(flexibility));
        }

        return out;
    }

    private ConsequenceInsight buildCashFlowInsight(
        FinancialBaseline baseline, ScenarioRequest scenario, BigDecimal monthlyImpact
    ) {
        // one-time: no monthly delta, but show the cash-buffer drawdown
        if (scenario.getType() == ScenarioType.ONE_TIME_PURCHASE) {
            return ConsequenceInsight.builder()
                .code("CASH_FLOW")
                .severity("CAUTION")
                .title("One-time cash buffer drawdown")
                .description(String.format(
                    "₹%,.0f outlay — no recurring impact, but reduces your liquid cash buffer.",
                    scenario.getAmount().doubleValue()))
                .build();
        }

        double impact = monthlyImpact.doubleValue();
        double discretionary = baseline.getMonthlyDiscretionary().doubleValue();

        if (impact > 0) {
            return ConsequenceInsight.builder()
                .code("SAVINGS_BOOST")
                .severity("POSITIVE")
                .title("Monthly cash flow improves")
                .description(String.format(
                    "Adds ₹%,.0f to your monthly net cash flow.", impact))
                .build();
        }

        double absImpact = Math.abs(impact);
        double newDiscretionary = Math.max(0, discretionary - absImpact);
        double dropPct = discretionary > 0 ? (absImpact / discretionary) * 100 : 100;

        String severity;
        if      (dropPct < 10) severity = "NEUTRAL";
        else if (dropPct < 25) severity = "CAUTION";
        else                   severity = "WARNING";

        String description = discretionary > 0
            ? String.format(
                "Monthly discretionary cash drops from ₹%,.0f to ₹%,.0f — a %.0f%% reduction.",
                discretionary, newDiscretionary, dropPct)
            : String.format("Adds ₹%,.0f to your monthly outgoings.", absImpact);

        return ConsequenceInsight.builder()
            .code("CASH_FLOW")
            .severity(severity)
            .title("Monthly cash flow tightens")
            .description(description)
            .build();
    }

    private ConsequenceInsight buildRecurringInsight(FinancialBaseline baseline, BigDecimal recurringDelta) {
        if (recurringDelta.compareTo(BigDecimal.ZERO) == 0) return null;

        double current = baseline.getMonthlyRecurring().doubleValue();
        double delta   = recurringDelta.doubleValue();
        double newVal  = current + delta;

        double changePct = current > 0 ? (delta / current) * 100 : 100;

        String severity;
        if      (delta < 0)         severity = "POSITIVE";
        else if (changePct < 10)    severity = "NEUTRAL";
        else if (changePct < 25)    severity = "CAUTION";
        else                        severity = "WARNING";

        String description = delta > 0
            ? String.format(
                "Recurring commitments climb from ₹%,.0f to ₹%,.0f/month (+%.0f%%).",
                current, newVal, changePct)
            : String.format(
                "Recurring commitments drop from ₹%,.0f to ₹%,.0f/month.",
                current, newVal);

        return ConsequenceInsight.builder()
            .code("RECURRING_LOAD")
            .severity(severity)
            .title(delta > 0 ? "Recurring obligations grow" : "Recurring obligations shrink")
            .description(description)
            .build();
    }

    private ConsequenceInsight buildCategoryInsight(FinancialBaseline baseline, ScenarioRequest scenario) {
        if (scenario.getCategory() == null) return null;
        if (baseline.getTopCategoryName() == null) return null;

        // hitting the existing top category intensifies concentration
        String scenarioCategoryName = scenario.getCategory().getDisplayName();
        boolean hitsTopCategory = scenarioCategoryName.equals(baseline.getTopCategoryName());

        if (!hitsTopCategory) {
            // would the scenario amount displace the top category?
            BigDecimal monthlyEquivalent = scenarioToMonthly(scenario);
            if (monthlyEquivalent.compareTo(baseline.getTopCategoryMonthlySpend()) > 0) {
                return ConsequenceInsight.builder()
                    .code("CATEGORY_SHIFT")
                    .severity("CAUTION")
                    .title(scenarioCategoryName + " would dominate your spending")
                    .description(String.format(
                        "At ₹%,.0f/month, %s would surpass your current top category (%s at ₹%,.0f/month).",
                        monthlyEquivalent.doubleValue(),
                        scenarioCategoryName,
                        baseline.getTopCategoryName(),
                        baseline.getTopCategoryMonthlySpend().doubleValue()))
                    .build();
            }
            return null;
        }

        return ConsequenceInsight.builder()
            .code("CATEGORY_SHIFT")
            .severity("CAUTION")
            .title("Concentrates your biggest spending area")
            .description(String.format(
                "%s is already your top category at ₹%,.0f/month. This decision deepens that concentration.",
                baseline.getTopCategoryName(), baseline.getTopCategoryMonthlySpend().doubleValue()))
            .build();
    }

    private ConsequenceInsight buildFlexibilityShiftInsight(FlexibilityScore f) {
        boolean improved = f.getProjectedScore() > f.getCurrentScore();
        return ConsequenceInsight.builder()
            .code("OPPORTUNITY_COST")
            .severity(improved ? "POSITIVE" : "WARNING")
            .title(String.format("Flexibility shifts: %s → %s",
                tierLabel(f.getCurrentTier()), tierLabel(f.getProjectedTier())))
            .description(f.getExplanation())
            .build();
    }

    private static String tierLabel(String tier) {
        return switch (tier) {
            case "EXCELLENT"   -> "Excellent";
            case "GOOD"        -> "Good";
            case "FAIR"        -> "Fair";
            case "TIGHT"       -> "Tight";
            case "CONSTRAINED" -> "Constrained";
            default            -> tier;
        };
    }

    // best-effort monthly equivalent for category comparison
    private static BigDecimal scenarioToMonthly(ScenarioRequest scenario) {
        return switch (scenario.getType()) {
            case ONE_TIME_PURCHASE   -> scenario.getAmount().divide(java.math.BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
            case RECURRING_EXPENSE   -> scenario.getAmount();
            case SAVINGS_ADJUSTMENT  -> scenario.getAmount().abs();
            case LOAN_EMI            -> scenario.getAmount(); // approximation — caller usually uses EMI
        };
    }
}
