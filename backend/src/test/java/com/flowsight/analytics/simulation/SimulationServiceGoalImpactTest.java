package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.ScenarioType;
import com.flowsight.dto.simulation.SimulationResult;
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

class SimulationServiceGoalImpactTest {

    private SimulationResult simulate(ScenarioRequest scenario) {
        BaselineCalculator baselineCalculator = mock(BaselineCalculator.class);
        when(baselineCalculator.compute(any())).thenReturn(FinancialBaseline.builder()
            .monthlyIncome(new BigDecimal("90000")).monthlySpend(new BigDecimal("65000"))
            .monthlyRecurring(new BigDecimal("30000")).monthlyDiscretionary(new BigDecimal("25000"))
            .monthlyNetSavings(new BigDecimal("25000")).savingsRate(0.28).dataMonths(3).hasEnoughData(true)
            .build());
        FinancialGoalRepository goals = mock(FinancialGoalRepository.class);
        when(goals.findByUserIdAndStatusOrderByTargetDateAsc(any(), eq(GoalStatus.ACTIVE))).thenReturn(List.of(
            FinancialGoal.builder().name("Emergency fund").targetAmount(new BigDecimal("100000"))
                .currentAmount(BigDecimal.ZERO).targetDate(LocalDate.now().plusDays(365)).build()));
        ProjectionEngine projection = new ProjectionEngine();

        return new SimulationService(baselineCalculator, new FlexibilityCalculator(), projection,
            new TradeoffAnalyzer(goals, projection), new ConsequenceInsightGenerator())
            .simulate(UUID.randomUUID(), scenario);
    }

    @Test
    void loanTenureLimitsHowLongTheEmiSlowsTheGoal() {
        ScenarioRequest loan = new ScenarioRequest();
        loan.setType(ScenarioType.LOAN_EMI);
        loan.setAmount(new BigDecimal("24000"));
        loan.setAnnualInterestRate(BigDecimal.ZERO);
        loan.setTenureMonths(3);

        SimulationResult r = simulate(loan);

        // EMI 8,000 for 3 months: 17,000 x 3 + 25,000 x 2 reaches 100,000 in month 5 instead of 4
        assertThat(r.getMonthlyImpact()).isEqualByComparingTo("-8000");
        assertThat(r.getGoalImpact().isReachable()).isTrue();
        assertThat(r.getGoalImpact().getDelayMonths()).isEqualTo(1);
    }

    @Test
    void openEndedRecurringExpenseSlowsTheGoalThroughout() {
        ScenarioRequest gym = new ScenarioRequest();
        gym.setType(ScenarioType.RECURRING_EXPENSE);
        gym.setAmount(new BigDecimal("8000"));

        SimulationResult r = simulate(gym);

        assertThat(r.getGoalImpact().getDelayMonths()).isEqualTo(2);
        assertThat(r.getInsights()).anySatisfy(i -> {
            assertThat(i.getCode()).isEqualTo("GOAL_DELAY");
            assertThat(i.getTitle()).isEqualTo("Goal completion delayed");
        });
    }
}
