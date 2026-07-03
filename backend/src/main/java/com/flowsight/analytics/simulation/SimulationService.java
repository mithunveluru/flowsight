package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

// Orchestrates a simulation: baseline, impact, flexibility, projection, tradeoffs, insights. Stateless.
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private static final double ASSUMED_ANNUAL_RETURN = 0.08;

    private final BaselineCalculator          baselineCalculator;
    private final FlexibilityCalculator       flexibilityCalculator;
    private final ProjectionEngine            projectionEngine;
    private final TradeoffAnalyzer            tradeoffAnalyzer;
    private final ConsequenceInsightGenerator insightGenerator;

    // Current flexibility with no scenario (baseline as its own projection). Backs the desktop gauge.
    public FlexibilityScore currentFlexibility(UUID userId) {
        FinancialBaseline current = baselineCalculator.compute(userId);
        return flexibilityCalculator.compute(current, current);
    }

    public SimulationResult simulate(UUID userId, ScenarioRequest scenario) {
        FinancialBaseline current = baselineCalculator.compute(userId);

        // scenario -> monthly + recurring deltas
        Deltas deltas = computeDeltas(scenario);
        FinancialBaseline projected = applyDeltasToBaseline(current, deltas);

        FlexibilityScore flexibility = flexibilityCalculator.compute(current, projected);
        Projection       projection  = projectionEngine.project(current, scenario, deltas.monthlyImpact());
        List<Tradeoff>   tradeoffs   = tradeoffAnalyzer.analyze(current, scenario);
        GoalImpact       goalImpact  = tradeoffAnalyzer.computeGoalImpact(userId, deltas.monthlyImpact(), current);
        List<ConsequenceInsight> insights = insightGenerator.generate(
            current, scenario, deltas.monthlyImpact(), deltas.recurringDelta(), flexibility, goalImpact);

        BigDecimal yearly      = deltas.monthlyImpact().multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fiveYear    = deltas.monthlyImpact().multiply(BigDecimal.valueOf(60)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tenYearCost = deltas.monthlyImpact().multiply(BigDecimal.valueOf(120)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tenYearOpportunityCost = computeOpportunityCost(scenario, deltas);

        // one-time: the yearly/5yr/10yr summary is the upfront amount, not 0
        if (scenario.getType() == ScenarioType.ONE_TIME_PURCHASE) {
            yearly      = scenario.getAmount().negate();
            fiveYear    = scenario.getAmount().negate();
            tenYearCost = scenario.getAmount().negate();
        }

        return SimulationResult.builder()
            .baseline(current)
            .scenario(scenario)
            .monthlyImpact(deltas.monthlyImpact())
            .yearlyImpact(yearly)
            .fiveYearImpact(fiveYear)
            .tenYearCost(tenYearCost)
            .tenYearOpportunityCost(tenYearOpportunityCost)
            .flexibility(flexibility)
            .projection(projection)
            .tradeoffs(tradeoffs)
            .insights(insights)
            .goalImpact(goalImpact)
            .build();
    }

    private Deltas computeDeltas(ScenarioRequest scenario) {
        return switch (scenario.getType()) {
            case ONE_TIME_PURCHASE -> new Deltas(
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );
            case RECURRING_EXPENSE -> new Deltas(
                scenario.getAmount().negate(),
                scenario.getAmount()                // grows recurring obligations
            );
            case SAVINGS_ADJUSTMENT -> new Deltas(
                scenario.getAmount(),               // signed (+ improves cash flow)
                BigDecimal.ZERO
            );
            case LOAN_EMI -> {
                BigDecimal emi = projectionEngine.computeEmi(scenario);
                yield new Deltas(emi.negate(), emi);
            }
        };
    }

    // projected baseline with deltas applied, for flexibility recomputation
    private FinancialBaseline applyDeltasToBaseline(FinancialBaseline current, Deltas deltas) {
        BigDecimal newRecurring   = current.getMonthlyRecurring().add(deltas.recurringDelta());
        BigDecimal newSpend       = current.getMonthlySpend()
            .add(deltas.recurringDelta().max(BigDecimal.ZERO));
        BigDecimal newNetSavings  = current.getMonthlyNetSavings().add(deltas.monthlyImpact());
        BigDecimal newDiscretionary = current.getMonthlyIncome()
            .subtract(newSpend).subtract(newRecurring.max(BigDecimal.ZERO))
            .max(BigDecimal.ZERO);

        double newRate = current.getMonthlyIncome().compareTo(BigDecimal.ZERO) > 0
            ? newNetSavings.doubleValue() / current.getMonthlyIncome().doubleValue() : 0.0;

        return FinancialBaseline.builder()
            .monthlyIncome(current.getMonthlyIncome())
            .monthlySpend(newSpend)
            .monthlyRecurring(newRecurring)
            .monthlyDiscretionary(newDiscretionary)
            .monthlyNetSavings(newNetSavings)
            .savingsRate(Math.max(-1.0, Math.min(1.0, newRate)))
            .dataMonths(current.getDataMonths())
            .topCategoryName(current.getTopCategoryName())
            .topCategoryMonthlySpend(current.getTopCategoryMonthlySpend())
            .hasEnoughData(current.isHasEnoughData())
            .build();
    }

    private BigDecimal computeOpportunityCost(ScenarioRequest scenario, Deltas deltas) {
        return switch (scenario.getType()) {
            case ONE_TIME_PURCHASE  -> tradeoffAnalyzer.futureValueLumpSum(
                scenario.getAmount(), ASSUMED_ANNUAL_RETURN, 10);
            case RECURRING_EXPENSE,
                 SAVINGS_ADJUSTMENT,
                 LOAN_EMI            -> tradeoffAnalyzer.futureValueAnnuity(
                deltas.monthlyImpact().abs(), ASSUMED_ANNUAL_RETURN, 120);
        };
    }

    // signed monthly impact (negative = costs more) + signed recurring delta (positive = grows)
    private record Deltas(BigDecimal monthlyImpact, BigDecimal recurringDelta) {}
}
