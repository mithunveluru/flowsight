package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.FinancialBaseline;
import com.flowsight.dto.simulation.MonteCarloResult;
import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.ScenarioType;
import com.flowsight.dto.simulation.SimulationResult;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.ml.forecast.MonthlyHistory;
import com.flowsight.repository.FinancialGoalRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationServiceMonteCarloTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    // 12 complete months: income 90,000; spend 65,000 or a swinging amount around it
    private static MonthlyHistory history(boolean swinging) {
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(12);
        List<MonthlyHistory.Txn> txns = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            LocalDate d = first.plusMonths(m);
            txns.add(new MonthlyHistory.Txn(d, TransactionType.CREDIT, TransactionCategory.INCOME, 90000));
            double spend = swinging ? 65000 + (m % 3 - 1) * 9000 : 65000;
            txns.add(new MonthlyHistory.Txn(d.plusDays(4), TransactionType.DEBIT, TransactionCategory.SHOPPING, spend));
        }
        return MonthlyHistory.of(txns, first, LocalDate.now());
    }

    private static SimulationService service(boolean swinging, boolean withGoal) {
        BaselineCalculator baseline = mock(BaselineCalculator.class);
        when(baseline.compute(any())).thenAnswer(i -> FinancialBaseline.builder()
            .monthlyIncome(new BigDecimal("90000")).monthlySpend(new BigDecimal("65000"))
            .monthlyRecurring(new BigDecimal("30000")).monthlyDiscretionary(new BigDecimal("25000"))
            .monthlyNetSavings(new BigDecimal("25000")).savingsRate(0.28).dataMonths(3).hasEnoughData(true)
            .build());
        FinancialGoalRepository goals = mock(FinancialGoalRepository.class);
        when(goals.findByUserIdAndStatusOrderByTargetDateAsc(any(), eq(GoalStatus.ACTIVE))).thenReturn(withGoal
            ? List.of(FinancialGoal.builder().name("Emergency fund").targetAmount(new BigDecimal("100000"))
                .currentAmount(BigDecimal.ZERO).targetDate(LocalDate.now().plusDays(190)).build())
            : List.of());
        ForecastService forecasts = mock(ForecastService.class);
        when(forecasts.history(any(), any())).thenReturn(history(swinging));
        ProjectionEngine projection = new ProjectionEngine();
        return new SimulationService(baseline, new FlexibilityCalculator(), projection,
            new TradeoffAnalyzer(goals, projection), new ConsequenceInsightGenerator(), forecasts, goals);
    }

    private static ScenarioRequest recurring(String amount) {
        ScenarioRequest r = new ScenarioRequest();
        r.setType(ScenarioType.RECURRING_EXPENSE);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void repeatingARequestGivesTheSameDistribution() {
        SimulationResult a = service(true, true).simulate(USER, recurring("5000"));
        SimulationResult b = service(true, true).simulate(USER, recurring("5000"));
        assertThat(a.getMonteCarlo()).isEqualTo(b.getMonteCarlo());
        assertThat(a.getMonteCarlo().getEvidence().getSeed()).isEqualTo(b.getMonteCarlo().getEvidence().getSeed());
        assertThat(a.getMonteCarlo().getEvidence().getPaths()).isEqualTo(2000);

        ScenarioRequest seeded = recurring("5000");
        seeded.setSeed(1L);
        seeded.setPaths(500);
        MonteCarloResult c = service(true, true).simulate(USER, seeded).getMonteCarlo();
        assertThat(c.getEvidence().getSeed()).isEqualTo(1L);
        assertThat(c.getEvidence().getPaths()).isEqualTo(500);
    }

    @Test
    void fanAndOddsHaveTheShapeTheUiDraws() {
        MonteCarloResult mc = service(true, true).simulate(USER, recurring("5000")).getMonteCarlo();
        assertThat(mc.getFan()).hasSize(12);
        assertThat(mc.getBaselineFan()).hasSize(12);
        for (MonteCarloResult.FanPoint p : mc.getFan()) {
            assertThat(p.p10()).isLessThanOrEqualTo(p.p50());
            assertThat(p.p50()).isLessThanOrEqualTo(p.p90());
        }
        assertThat(mc.getFan().get(11).p50()).isLessThan(mc.getBaselineFan().get(11).p50());
        MonteCarloResult.GoalOdds goal = mc.getGoal();
        assertThat(goal.getReachProbabilityByMonth()).hasSize(120);
        assertThat(goal.getProbabilityByTargetDate()).isLessThanOrEqualTo(goal.getBaselineProbabilityByTargetDate());
        assertThat(goal.getMonthsToTarget()).isEqualTo(6);
        assertThat(mc.getFlexibility().p10()).isLessThanOrEqualTo(mc.getFlexibility().p50());
        assertThat(mc.getFlexibility().p50()).isLessThanOrEqualTo(mc.getFlexibility().p90());
        assertThat(mc.getEvidence().getVolatilitySource()).isEqualTo("OWN_HISTORY");
        assertThat(mc.getEvidence().getResidualMonths()).isEqualTo(12);
    }

    @Test
    void aFlatHistoryStillCarriesThePriorSwingsButCentersOnTheDeterministicLine() {
        SimulationResult r = service(false, false).simulate(USER, recurring("5000"));
        MonteCarloResult mc = r.getMonteCarlo();
        assertThat(mc.getGoal()).isNull();
        assertThat(mc.getEvidence().getOwnMonthlySd()).isEqualByComparingTo("0");
        assertThat(mc.getEvidence().getVolatilitySource()).isEqualTo("PRIOR");
        // median of 12 months of normal swings sits near the deterministic 240,000
        BigDecimal deterministic = r.getProjection().getAfter().get(11).getCumulativeSavings();
        assertThat(mc.getFan().get(11).p50().subtract(deterministic).abs()).isLessThan(new BigDecimal("6000"));
    }

    @Test
    void largerCostsLowerTheGoalOdds() {
        double previous = 1.1;
        for (String cost : new String[] {"0", "4000", "9000", "15000"}) {
            double p = service(true, true).simulate(USER, recurring(cost)).getMonteCarlo().getGoal().getProbabilityByTargetDate();
            assertThat(p).isLessThanOrEqualTo(previous);
            previous = p;
        }
    }

    @Test
    void theReturnIsALabeledAssumptionTheUserCanChange() {
        SimulationResult byDefault = service(true, false).simulate(USER, recurring("5000"));
        assertThat(byDefault.getAssumptions().annualReturnPercent()).isEqualTo(8.0);
        assertThat(byDefault.getAssumptions().annualReturnSource()).isEqualTo("DEFAULT");
        assertThat(byDefault.getAssumptions().label()).contains("8%").contains("assumption");

        ScenarioRequest zero = recurring("5000");
        zero.setAnnualReturnPercent(BigDecimal.ZERO);
        SimulationResult r = service(true, false).simulate(USER, zero);
        assertThat(r.getAssumptions().annualReturnSource()).isEqualTo("USER");
        assertThat(r.getTenYearOpportunityCost()).isEqualByComparingTo("600000");
        assertThat(r.getTradeoffs()).anySatisfy(t -> assertThat(t.getDescription()).contains("assumed 0% annual return"));
        assertThat(byDefault.getTenYearOpportunityCost()).isGreaterThan(r.getTenYearOpportunityCost());
    }

    @Test
    void aOneTimePurchaseNowDelaysTheGoal() {
        ScenarioRequest laptop = new ScenarioRequest();
        laptop.setType(ScenarioType.ONE_TIME_PURCHASE);
        laptop.setAmount(new BigDecimal("30000"));
        SimulationResult r = service(true, true).simulate(USER, laptop);
        // 100,000 at 25,000/month: month 4; with 30,000 paid first: month 6 (130,000 / 25,000 = 5.2)
        assertThat(r.getGoalImpact().getDelayMonths()).isEqualTo(2);
        assertThat(r.getGoalImpact().getDescription()).contains("up front");
    }
}
