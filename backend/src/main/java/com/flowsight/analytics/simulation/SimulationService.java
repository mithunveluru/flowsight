package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.*;
import com.flowsight.entity.FinancialGoal;
import com.flowsight.entity.GoalStatus;
import com.flowsight.ml.forecast.ForecastPriors;
import com.flowsight.ml.forecast.ForecastService;
import com.flowsight.repository.FinancialGoalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Orchestrates a simulation: baseline, impact, flexibility, projection, tradeoffs, insights,
// and the Monte Carlo distribution around them. Stateless.
@Service
@Slf4j
public class SimulationService {

    static final int    DEFAULT_PATHS  = 2000;
    static final int    FAN_MONTHS     = 12;
    private static final double DAYS_PER_MONTH = 365.25 / 12;

    private final BaselineCalculator          baselineCalculator;
    private final FlexibilityCalculator       flexibilityCalculator;
    private final ProjectionEngine            projectionEngine;
    private final TradeoffAnalyzer            tradeoffAnalyzer;
    private final ConsequenceInsightGenerator insightGenerator;
    private final ForecastService             forecastService;
    private final FinancialGoalRepository     goalRepository;
    private final ForecastPriors              priors;

    @Autowired
    public SimulationService(
        BaselineCalculator baselineCalculator, FlexibilityCalculator flexibilityCalculator,
        ProjectionEngine projectionEngine, TradeoffAnalyzer tradeoffAnalyzer,
        ConsequenceInsightGenerator insightGenerator, ForecastService forecastService,
        FinancialGoalRepository goalRepository
    ) {
        this(baselineCalculator, flexibilityCalculator, projectionEngine, tradeoffAnalyzer, insightGenerator,
            forecastService, goalRepository, ForecastPriors.bundled());
    }

    SimulationService(
        BaselineCalculator baselineCalculator, FlexibilityCalculator flexibilityCalculator,
        ProjectionEngine projectionEngine, TradeoffAnalyzer tradeoffAnalyzer,
        ConsequenceInsightGenerator insightGenerator, ForecastService forecastService,
        FinancialGoalRepository goalRepository, ForecastPriors priors
    ) {
        this.baselineCalculator    = baselineCalculator;
        this.flexibilityCalculator = flexibilityCalculator;
        this.projectionEngine      = projectionEngine;
        this.tradeoffAnalyzer      = tradeoffAnalyzer;
        this.insightGenerator      = insightGenerator;
        this.forecastService       = forecastService;
        this.goalRepository        = goalRepository;
        this.priors                = priors;
    }

    // Current flexibility with no scenario (baseline as its own projection). Backs the desktop gauge.
    public FlexibilityScore currentFlexibility(UUID userId) {
        FinancialBaseline current = baselineCalculator.compute(userId);
        if (current.isHasEnoughData()) current.setMonthlyNetVolatility(money(volatility(userId).sd()));
        return flexibilityCalculator.compute(current, current);
    }

    public SimulationResult simulate(UUID userId, ScenarioRequest scenario) {
        FinancialBaseline current = baselineCalculator.compute(userId);
        CashFlowVolatility volatility = current.isHasEnoughData() ? volatility(userId) : null;
        if (volatility != null) current.setMonthlyNetVolatility(money(volatility.sd()));
        SimulationAssumptions assumed = SimulationAssumptions.of(scenario.getAnnualReturnPercent());

        // scenario -> monthly + recurring deltas
        Deltas deltas = computeDeltas(scenario);
        FinancialBaseline projected = applyDeltasToBaseline(current, deltas);

        FlexibilityScore flexibility = flexibilityCalculator.compute(current, projected);
        Projection       projection  = projectionEngine.project(current, scenario, deltas.monthlyImpact());
        List<Tradeoff>   tradeoffs   = tradeoffAnalyzer.analyze(current, scenario, assumed);
        BigDecimal       upfront     = scenario.getType() == ScenarioType.ONE_TIME_PURCHASE
            ? scenario.getAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        GoalImpact       goalImpact  = tradeoffAnalyzer.computeGoalImpact(
            userId, deltas.monthlyImpact(), impactMonths(scenario), upfront, current);
        List<ConsequenceInsight> insights = insightGenerator.generate(
            current, scenario, deltas.monthlyImpact(), deltas.recurringDelta(), flexibility, goalImpact);

        BigDecimal yearly      = deltas.monthlyImpact().multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fiveYear    = deltas.monthlyImpact().multiply(BigDecimal.valueOf(60)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tenYearCost = deltas.monthlyImpact().multiply(BigDecimal.valueOf(120)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tenYearOpportunityCost = computeOpportunityCost(scenario, deltas, assumed);

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
            .assumptions(assumed)
            .monteCarlo(volatility == null ? null : monteCarlo(userId, scenario, current, projected, volatility))
            .build();
    }

    private CashFlowVolatility volatility(UUID userId) {
        return CashFlowVolatility.of(forecastService.history(userId, LocalDate.now()), priors);
    }

    MonteCarloResult monteCarlo(UUID userId, ScenarioRequest scenario, FinancialBaseline current,
                                FinancialBaseline projected, CashFlowVolatility vol) {
        List<FinancialGoal> goals = goalRepository.findByUserIdAndStatusOrderByTargetDateAsc(userId, GoalStatus.ACTIVE);
        FinancialGoal goal = goals.isEmpty() ? null : goals.get(0);
        BigDecimal remaining = goal == null ? null : goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (remaining != null && remaining.signum() <= 0) {
            goal = null;
            remaining = null;
        }
        int horizon = goal == null ? FAN_MONTHS : TradeoffAnalyzer.GOAL_HORIZON_MONTHS;
        int paths = scenario.getPaths() != null ? scenario.getPaths() : DEFAULT_PATHS;
        long seed = scenario.getSeed() != null ? scenario.getSeed() : seedFor(userId);
        double baseNet = current.getMonthlyNetSavings().doubleValue();

        MonteCarloSimulator.Paths run = MonteCarloSimulator.simulate(new MonteCarloSimulator.Inputs(
            baseNet, vol.residuals(), vol.sd(), monthlyDeltas(scenario, horizon), paths, seed,
            remaining == null ? null : remaining.doubleValue()));

        MonteCarloResult.GoalOdds odds = null;
        if (goal != null) {
            int monthsToTarget = (int) Math.max(0, Math.floor(
                ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate()) / DAYS_PER_MONTH));
            List<Double> byMonth = new ArrayList<>();
            for (int m = 1; m <= horizon; m++) byMonth.add(round4(MonteCarloSimulator.reachedBy(run.reachMonth(), m)));
            odds = MonteCarloResult.GoalOdds.builder()
                .goalName(goal.getName())
                .remaining(remaining.setScale(2, RoundingMode.HALF_UP))
                .targetDate(goal.getTargetDate())
                .monthsToTarget(monthsToTarget)
                .probabilityByTargetDate(round4(MonteCarloSimulator.reachedBy(run.reachMonth(), monthsToTarget)))
                .baselineProbabilityByTargetDate(round4(MonteCarloSimulator.reachedBy(run.baselineReachMonth(), monthsToTarget)))
                .probabilityWithinHorizon(round4(MonteCarloSimulator.reachedBy(run.reachMonth(), horizon)))
                .reachMonth(reachMonths(run.reachMonth()))
                .baselineReachMonth(reachMonths(run.baselineReachMonth()))
                .reachProbabilityByMonth(byMonth)
                .build();
        }

        return MonteCarloResult.builder()
            .fan(fan(run.cumulative()))
            .baselineFan(fan(run.baselineCumulative()))
            .goal(odds)
            .flexibility(flexibilityPercentiles(run.cumulative(), projected))
            .baselineFlexibility(flexibilityPercentiles(run.baselineCumulative(), current))
            .evidence(MonteCarloResult.Evidence.builder()
                .method(vol.residuals().length > 0
                    ? "block bootstrap of your monthly net cash flow deviations"
                    : "normal monthly deviations at the prior volatility")
                .paths(paths)
                .seed(seed)
                .horizonMonths(horizon)
                .blockLength(MonteCarloSimulator.BLOCK_LENGTH)
                .residualMonths(vol.months())
                .window(vol.window())
                .baseMonthlyNet(money(baseNet))
                .ownMonthlySd(vol.ownSd() == null ? null : money(vol.ownSd()))
                .priorMonthlySd(money(vol.priorSd()))
                .monthlySd(money(vol.sd()))
                .ownWeight(round4(vol.ownWeight()))
                .volatilitySource(vol.source())
                .note("Each path is your average monthly savings (" + money(baseNet).toPlainString()
                    + ") plus month-to-month swings like yours, plus the scenario. The swings come from "
                    + vol.months() + " complete " + (vol.months() == 1 ? "month" : "months")
                    + " of history blended with a global prior (" + Math.round(vol.ownWeight() * 100)
                    + "% your own). No investment return is applied to savings.")
                .build())
            .build();
    }

    private static List<MonteCarloResult.FanPoint> fan(double[][] cumulative) {
        List<MonteCarloResult.FanPoint> out = new ArrayList<>();
        int months = Math.min(FAN_MONTHS, cumulative.length == 0 ? 0 : cumulative[0].length);
        double[] column = new double[cumulative.length];
        for (int m = 0; m < months; m++) {
            for (int p = 0; p < cumulative.length; p++) column[p] = cumulative[p][m];
            out.add(new MonteCarloResult.FanPoint(m + 1, money(MonteCarloSimulator.quantile(column, 0.1)),
                money(MonteCarloSimulator.quantile(column, 0.5)), money(MonteCarloSimulator.quantile(column, 0.9))));
        }
        return out;
    }

    // flexibility of each path's average monthly net over the first year
    private MonteCarloResult.Percentiles flexibilityPercentiles(double[][] cumulative, FinancialBaseline base) {
        double[] scores = new double[cumulative.length];
        int months = Math.min(FAN_MONTHS, cumulative[0].length);
        for (int p = 0; p < cumulative.length; p++) {
            BigDecimal net = money(cumulative[p][months - 1] / months);
            FinancialBaseline path = FinancialBaseline.builder()
                .monthlyIncome(base.getMonthlyIncome())
                .monthlySpend(base.getMonthlyIncome().subtract(net))
                .monthlyRecurring(base.getMonthlyRecurring())
                .monthlyDiscretionary(net.max(BigDecimal.ZERO))
                .monthlyNetSavings(net)
                .dataMonths(base.getDataMonths())
                .hasEnoughData(base.isHasEnoughData())
                .monthlyNetVolatility(base.getMonthlyNetVolatility())
                .build();
            scores[p] = flexibilityCalculator.scoreFrom(path);
        }
        return new MonteCarloResult.Percentiles(MonteCarloSimulator.quantile(scores, 0.1),
            MonteCarloSimulator.quantile(scores, 0.5), MonteCarloSimulator.quantile(scores, 0.9));
    }

    private static MonteCarloResult.ReachMonths reachMonths(int[] reach) {
        return new MonteCarloResult.ReachMonths(MonteCarloSimulator.reachQuantile(reach, 0.1),
            MonteCarloSimulator.reachQuantile(reach, 0.5), MonteCarloSimulator.reachQuantile(reach, 0.9));
    }

    // the scenario's change to monthly net for months 1..horizon; open-ended recurring changes last throughout
    double[] monthlyDeltas(ScenarioRequest scenario, int horizon) {
        double[] out = new double[horizon];
        double amount = scenario.getAmount().doubleValue();
        switch (scenario.getType()) {
            case ONE_TIME_PURCHASE -> out[0] = -amount;
            case RECURRING_EXPENSE -> fill(out, -amount, scenario.getDurationMonths());
            case SAVINGS_ADJUSTMENT -> fill(out, amount, scenario.getDurationMonths());
            case LOAN_EMI -> fill(out, -projectionEngine.computeEmi(scenario).doubleValue(), scenario.getTenureMonths());
        }
        return out;
    }

    private static void fill(double[] out, double v, Integer months) {
        int n = months == null ? out.length : Math.min(months, out.length);
        for (int m = 0; m < n; m++) out[m] = v;
    }

    // Same user, same draws: a repeated request gives the same answer, and two scenarios are compared
    // on identical swings, so their difference is the scenario alone.
    static long seedFor(UUID userId) {
        long h = userId.getMostSignificantBits() * 0x9E3779B97F4A7C15L + userId.getLeastSignificantBits();
        return h ^ (h >>> 31);
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
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

    // how long the monthly impact lasts; null = open-ended
    private static Integer impactMonths(ScenarioRequest scenario) {
        return switch (scenario.getType()) {
            case LOAN_EMI -> scenario.getTenureMonths();
            case ONE_TIME_PURCHASE, RECURRING_EXPENSE, SAVINGS_ADJUSTMENT -> scenario.getDurationMonths();
        };
    }

    // projected baseline with deltas applied, for flexibility recomputation
    private FinancialBaseline applyDeltasToBaseline(FinancialBaseline current, Deltas deltas) {
        BigDecimal newRecurring   = current.getMonthlyRecurring().add(deltas.recurringDelta());
        BigDecimal newSpend       = current.getMonthlySpend()
            .add(deltas.recurringDelta().max(BigDecimal.ZERO));
        BigDecimal newNetSavings  = current.getMonthlyNetSavings().add(deltas.monthlyImpact());
        BigDecimal newDiscretionary = current.getMonthlyIncome().subtract(newSpend).max(BigDecimal.ZERO);

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
            .monthlyNetVolatility(current.getMonthlyNetVolatility())
            .build();
    }

    private BigDecimal computeOpportunityCost(ScenarioRequest scenario, Deltas deltas, SimulationAssumptions assumed) {
        return switch (scenario.getType()) {
            case ONE_TIME_PURCHASE  -> tradeoffAnalyzer.futureValueLumpSum(
                scenario.getAmount(), assumed.annualRate(), 10);
            case RECURRING_EXPENSE,
                 SAVINGS_ADJUSTMENT,
                 LOAN_EMI            -> tradeoffAnalyzer.futureValueAnnuity(
                deltas.monthlyImpact().abs(), assumed.annualRate(), 120);
        };
    }

    // signed monthly impact (negative = costs more) + signed recurring delta (positive = grows)
    private record Deltas(BigDecimal monthlyImpact, BigDecimal recurringDelta) {}
}
