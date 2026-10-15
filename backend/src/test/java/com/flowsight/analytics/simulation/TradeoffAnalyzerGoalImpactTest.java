package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.ConsequenceInsight;
import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.dto.simulation.FlexibilityScore;
import com.flowsight.dto.simulation.GoalImpact;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.ScenarioType;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.repository.FinancialGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeoffAnalyzerGoalImpactTest {

    private GoalImpact impact(String target, int daysAhead, String monthlyImpact, Integer impactMonths,
                              FinancialBaseline baseline) {
        FinancialGoalRepository repo = mock(FinancialGoalRepository.class);
        FinancialGoal goal = FinancialGoal.builder()
            .name("Gym supplements")
            .targetAmount(new BigDecimal(target))
            .currentAmount(BigDecimal.ZERO)
            .targetDate(LocalDate.now().plusDays(daysAhead))
            .status(GoalStatus.ACTIVE)
            .build();
        when(repo.findByUserIdAndStatusOrderByTargetDateAsc(any(), eq(GoalStatus.ACTIVE))).thenReturn(List.of(goal));
        return new TradeoffAnalyzer(repo, new ProjectionEngine())
            .computeGoalImpact(UUID.randomUUID(), new BigDecimal(monthlyImpact), impactMonths, baseline);
    }

    private static FinancialBaseline saving(String income, String spend) {
        BigDecimal in = new BigDecimal(income), out = new BigDecimal(spend);
        return FinancialBaseline.builder()
            .monthlyIncome(in)
            .monthlySpend(out)
            .monthlyRecurring(BigDecimal.ZERO)
            .monthlyDiscretionary(in.subtract(out).max(BigDecimal.ZERO))
            .monthlyNetSavings(in.subtract(out))
            .dataMonths(3)
            .hasEnoughData(true)
            .build();
    }

    @Test
    void emiLargerThanSavings_reportsGoalUnreachableInsteadOfThousandsOfMonths() {
        // EMI 32,033/month against a 10,000 goal due in ~6 months, saving 20,000/month
        GoalImpact gi = impact("10000", 180, "-32033", 36, saving("85000", "65000"));

        assertThat(gi.isReachable()).isFalse();
        assertThat(gi.getDelayMonths()).isZero();
        assertThat(gi.getDescription())
            .isEqualTo("This takes your monthly savings from ₹20,000 to -₹12,033, leaving nothing to put "
                + "toward 'Gym supplements'. At this pace the goal can't be reached.");
    }

    @Test
    void recurringCostBelowSavings_delayComesFromActualSavingsPace() {
        // 100,000 goal due in a year, 8,000/month cost, saving 25,000/month: 4 months -> 6 months
        GoalImpact gi = impact("100000", 365, "-8000", null, saving("90000", "65000"));

        assertThat(gi.isReachable()).isTrue();
        assertThat(gi.getDelayMonths()).isEqualTo(2);
        assertThat(gi.getDescription())
            .isEqualTo("Pushes 'Gym supplements' back by about 2 months: from 4 to 6 months at your current savings pace.");
    }

    @Test
    void costEndingAfterTenure_onlySlowsTheMonthsItRuns() {
        // 8,000/month for 3 months: 17,000 x 3 + 25,000 x 2 covers 100,000 in month 5
        GoalImpact gi = impact("100000", 365, "-8000", 3, saving("90000", "65000"));

        assertThat(gi.getDelayMonths()).isEqualTo(1);
        assertThat(gi.getDescription()).contains("from 4 to 5 months");
    }

    @Test
    void delayPastTargetDate_saysSo() {
        // saving 40,000: 3 months; with a 15,000 cost: 4 months, past a target ~4 months away minus a few days
        GoalImpact gi = impact("100000", 120, "-15000", null, saving("100000", "60000"));

        assertThat(gi.getDelayMonths()).isEqualTo(1);
        assertThat(gi.getDescription()).contains("later than its target date");
    }

    @Test
    void slowerThanTenYears_isReportedUnreachable() {
        GoalImpact gi = impact("100000", 365, "-9500", null, saving("60000", "50000"));

        assertThat(gi.isReachable()).isFalse();
        assertThat(gi.getDelayMonths()).isZero();
        assertThat(gi.getDescription())
            .isEqualTo("At ₹500/month of savings, 'Gym supplements' would take more than 10 years to reach.");
    }

    @Test
    void negativeSavingsBeforeScenario_isUnreachable() {
        GoalImpact gi = impact("50000", 365, "-5000", null, saving("40000", "42000"));

        assertThat(gi.isReachable()).isFalse();
        assertThat(gi.getDescription()).contains("from -₹2,000 to -₹7,000");
    }

    @Test
    void costTooSmallToShiftTheMonth_hasNoImpact() {
        assertThat(impact("100000", 365, "-100", null, saving("90000", "60000"))).isNull();
    }

    @Test
    void noBaselineData_hasNoImpact() {
        FinancialBaseline empty = FinancialBaseline.builder()
            .monthlyIncome(BigDecimal.ZERO).monthlySpend(BigDecimal.ZERO)
            .monthlyRecurring(BigDecimal.ZERO).monthlyDiscretionary(BigDecimal.ZERO)
            .monthlyNetSavings(BigDecimal.ZERO).hasEnoughData(false).build();

        assertThat(impact("10000", 180, "-32033", 36, empty)).isNull();
    }

    @Test
    void savingsBoost_hasNoImpact() {
        assertThat(impact("10000", 180, "5000", null, saving("85000", "65000"))).isNull();
    }

    @Test
    void insightTitleFollowsReachability() {
        ScenarioRequest loan = new ScenarioRequest();
        loan.setType(ScenarioType.LOAN_EMI);
        loan.setAmount(new BigDecimal("1000000"));
        FlexibilityScore sameTier = FlexibilityScore.builder()
            .currentTier("FAIR").projectedTier("FAIR").explanation("").build();
        FinancialBaseline baseline = saving("85000", "65000");
        ConsequenceInsightGenerator generator = new ConsequenceInsightGenerator();

        GoalImpact unreachable = impact("10000", 180, "-32033", 36, baseline);
        GoalImpact delayed     = impact("100000", 365, "-8000", null, saving("90000", "65000"));

        assertThat(goalInsight(generator.generate(baseline, loan, new BigDecimal("-32033"), BigDecimal.ZERO, sameTier, unreachable)).getTitle())
            .isEqualTo("Goal out of reach at this pace");
        assertThat(goalInsight(generator.generate(baseline, loan, new BigDecimal("-8000"), BigDecimal.ZERO, sameTier, delayed)).getTitle())
            .isEqualTo("Goal completion delayed");
    }

    private static ConsequenceInsight goalInsight(List<ConsequenceInsight> insights) {
        return insights.stream().filter(i -> "GOAL_DELAY".equals(i.getCode())).findFirst().orElseThrow();
    }
}
