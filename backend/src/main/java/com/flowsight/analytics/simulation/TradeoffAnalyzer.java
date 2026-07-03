package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.*;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.repository.FinancialGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Concrete tradeoffs of a scenario: save-up time, cumulative/opportunity cost, loan, goal impact.
@Service
@RequiredArgsConstructor
public class TradeoffAnalyzer {

    private static final double ASSUMED_ANNUAL_RETURN = 0.08;

    private final FinancialGoalRepository goalRepository;
    private final ProjectionEngine        projectionEngine;

    public List<Tradeoff> analyze(FinancialBaseline baseline, ScenarioRequest scenario) {
        List<Tradeoff> tradeoffs = new ArrayList<>();

        switch (scenario.getType()) {
            case ONE_TIME_PURCHASE   -> addOneTimeTradeoffs(tradeoffs, baseline, scenario);
            case RECURRING_EXPENSE   -> addRecurringTradeoffs(tradeoffs, scenario);
            case SAVINGS_ADJUSTMENT  -> addSavingsTradeoffs(tradeoffs, scenario);
            case LOAN_EMI            -> addLoanTradeoffs(tradeoffs, scenario);
        }

        return tradeoffs;
    }

    // goal impact for the user's nearest active goal, or null
    public GoalImpact computeGoalImpact(UUID userId, BigDecimal monthlyImpact, FinancialBaseline baseline) {
        if (monthlyImpact.compareTo(BigDecimal.ZERO) >= 0) return null; // boost or neutral — no delay

        List<FinancialGoal> goals = goalRepository
            .findByUserIdAndStatusOrderByTargetDateAsc(userId, GoalStatus.ACTIVE);
        if (goals.isEmpty()) return null;

        FinancialGoal goal = goals.get(0);
        LocalDate today = LocalDate.now();
        long daysToTarget = ChronoUnit.DAYS.between(today, goal.getTargetDate());
        if (daysToTarget <= 0) return null;

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return null;

        // monthly contribution needed to hit the goal on time
        double monthsToTarget = daysToTarget / 30.0;
        double originalMonthly = remaining.doubleValue() / monthsToTarget;

        // monthlyImpact is negative (a cost); flip to get the reduction in available savings
        double reduction = -monthlyImpact.doubleValue();
        double newMonthly = Math.max(1, originalMonthly - reduction);

        double newMonthsToTarget = remaining.doubleValue() / newMonthly;
        int delayMonths = Math.max(0, (int) Math.round(newMonthsToTarget - monthsToTarget));

        if (delayMonths == 0) return null;

        return GoalImpact.builder()
            .goalName(goal.getName())
            .delayMonths(delayMonths)
            .description(String.format(
                "Delays your '%s' goal by approximately %d %s.",
                goal.getName(), delayMonths, delayMonths == 1 ? "month" : "months"))
            .build();
    }

    private void addOneTimeTradeoffs(List<Tradeoff> out, FinancialBaseline baseline, ScenarioRequest scenario) {
        BigDecimal amount = scenario.getAmount();

        // months to save up at current net savings
        if (baseline.getMonthlyNetSavings().compareTo(BigDecimal.ZERO) > 0) {
            int months = (int) Math.ceil(amount.doubleValue() / baseline.getMonthlyNetSavings().doubleValue());
            out.add(t("Months to save up",
                months + (months == 1 ? " month" : " months"),
                "At your current pace of ₹" + fmtINR(baseline.getMonthlyNetSavings()) + "/month saved."));
        }

        if (baseline.getMonthlySpend().compareTo(BigDecimal.ZERO) > 0) {
            double dailySpend = baseline.getMonthlySpend().doubleValue() / 30.0;
            int days = (int) Math.round(amount.doubleValue() / dailySpend);
            out.add(t("Days of typical spending",
                days + (days == 1 ? " day" : " days"),
                "Based on your current daily spending pattern."));
        }

        out.add(t("10-year opportunity cost",
            "₹" + fmtINR(futureValueLumpSum(amount, ASSUMED_ANNUAL_RETURN, 10)),
            "If this amount were invested today at 8% annual return."));
    }

    private void addRecurringTradeoffs(List<Tradeoff> out, ScenarioRequest scenario) {
        BigDecimal monthly = scenario.getAmount();
        int duration = scenario.getDurationMonths() != null ? scenario.getDurationMonths() : 120;

        out.add(t("Total over " + duration + " " + (duration == 1 ? "month" : "months"),
            "₹" + fmtINR(monthly.multiply(BigDecimal.valueOf(duration))),
            "Cumulative cost across the chosen duration."));

        BigDecimal annual = monthly.multiply(BigDecimal.valueOf(12));
        out.add(t("Annual commitment",
            "₹" + fmtINR(annual),
            "Total spend per year if kept active."));

        out.add(t("10-year opportunity cost",
            "₹" + fmtINR(futureValueAnnuity(monthly, ASSUMED_ANNUAL_RETURN, 120)),
            "If you invested the same monthly amount at 8% annual return."));
    }

    private void addSavingsTradeoffs(List<Tradeoff> out, ScenarioRequest scenario) {
        BigDecimal monthly = scenario.getAmount().abs();
        int duration = scenario.getDurationMonths() != null ? scenario.getDurationMonths() : 120;

        boolean positive = scenario.getAmount().compareTo(BigDecimal.ZERO) > 0;

        out.add(t(positive ? "Saved over " + duration + " months" : "Reduced savings over " + duration + " months",
            "₹" + fmtINR(monthly.multiply(BigDecimal.valueOf(duration))),
            positive ? "Cumulative additional savings." : "Cumulative savings lost."));

        out.add(t("10-year compounded value",
            "₹" + fmtINR(futureValueAnnuity(monthly, ASSUMED_ANNUAL_RETURN, 120)),
            "If sustained for 10 years at 8% annual return."));
    }

    private void addLoanTradeoffs(List<Tradeoff> out, ScenarioRequest scenario) {
        BigDecimal emi = projectionEngine.computeEmi(scenario);
        int tenure = scenario.getTenureMonths() != null ? scenario.getTenureMonths() : 0;
        BigDecimal totalPaid = emi.multiply(BigDecimal.valueOf(tenure));
        BigDecimal totalInterest = totalPaid.subtract(scenario.getAmount()).max(BigDecimal.ZERO);

        out.add(t("Monthly EMI",
            "₹" + fmtINR(emi),
            "Fixed payment every month for " + tenure + " months."));

        out.add(t("Total interest paid",
            "₹" + fmtINR(totalInterest),
            "Over the full " + tenure + "-month tenure."));

        out.add(t("Total repayment",
            "₹" + fmtINR(totalPaid),
            "Principal + interest combined."));
    }

    // FV of a lump sum: P × (1+r)^n
    public BigDecimal futureValueLumpSum(BigDecimal principal, double annualRate, int years) {
        double fv = principal.doubleValue() * Math.pow(1 + annualRate, years);
        return BigDecimal.valueOf(fv).setScale(2, RoundingMode.HALF_UP);
    }

    // FV of a monthly annuity: P × ((1+r)^n − 1) / r
    public BigDecimal futureValueAnnuity(BigDecimal monthly, double annualRate, int months) {
        double r = annualRate / 12.0;
        double fv = monthly.doubleValue() * (Math.pow(1 + r, months) - 1) / r;
        return BigDecimal.valueOf(fv).setScale(2, RoundingMode.HALF_UP);
    }

    private static Tradeoff t(String label, String value, String description) {
        return Tradeoff.builder().label(label).value(value).description(description).build();
    }

    private static String fmtINR(BigDecimal v) {
        return String.format("%,.0f", v.doubleValue());
    }
}
