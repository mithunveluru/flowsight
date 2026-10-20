package com.flowsight.analytics.simulation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MonteCarloSimulatorTest {

    private static final double[] SWINGS = {-6000, 2000, 4000, -1000, 3000, -2000, 0, 5000, -5000};

    private static MonteCarloSimulator.Inputs inputs(double[] residuals, double[] deltas, long seed, Double goal) {
        return new MonteCarloSimulator.Inputs(10000, residuals, 3000, deltas, 1000, seed, goal);
    }

    private static double[] constant(int months, double v) {
        double[] d = new double[months];
        Arrays.fill(d, v);
        return d;
    }

    @Test
    void theSameSeedGivesTheSamePathsAndAnotherSeedDoesNot() {
        MonteCarloSimulator.Paths a = MonteCarloSimulator.simulate(inputs(SWINGS, constant(24, -500), 42, 90000.0));
        MonteCarloSimulator.Paths b = MonteCarloSimulator.simulate(inputs(SWINGS, constant(24, -500), 42, 90000.0));
        MonteCarloSimulator.Paths c = MonteCarloSimulator.simulate(inputs(SWINGS, constant(24, -500), 43, 90000.0));
        assertThat(a.cumulative()).isDeepEqualTo(b.cumulative());
        assertThat(a.reachMonth()).isEqualTo(b.reachMonth());
        assertThat(java.util.Arrays.deepEquals(a.cumulative(), c.cumulative())).isFalse();
    }

    @Test
    void zeroVolatilityReducesToTheDeterministicProjection() {
        double[] deltas = constant(12, -2500);
        deltas[0] -= 20000;
        MonteCarloSimulator.Paths run = MonteCarloSimulator.simulate(inputs(new double[6], deltas, 7, 60000.0));
        double expected = 0;
        int expectedReach = 0;
        for (int m = 0; m < 12; m++) {
            expected += 10000 + deltas[m];
            if (expectedReach == 0 && expected >= 60000) expectedReach = m + 1;
            for (double[] path : run.cumulative()) assertThat(path[m]).isEqualTo(expected);
        }
        assertThat(run.reachMonth()).containsOnly(expectedReach);
        assertThat(run.baselineReachMonth()).containsOnly(6);
        assertThat(MonteCarloSimulator.reachQuantile(run.reachMonth(), 0.1)).isEqualTo(expectedReach);
    }

    @Test
    void residualsAreResampledInBlocksOfTheUsersOwnMonths() {
        MonteCarloSimulator.Paths run = MonteCarloSimulator.simulate(inputs(SWINGS, new double[12], 3, null));
        for (double[] path : run.baselineCumulative()) {
            double prev = 0;
            for (double v : path) {
                double swing = v - prev - 10000;
                assertThat(SWINGS).contains(Math.round(swing) * 1.0);
                prev = v;
            }
        }
    }

    @Test
    void withTooLittleHistoryTheDrawsAreNormalAtTheFallbackSd() {
        MonteCarloSimulator.Paths run = MonteCarloSimulator.simulate(
            new MonteCarloSimulator.Inputs(0, new double[] {1, -1}, 3000, new double[1], 20000, 11, null));
        double[] first = Arrays.stream(run.cumulative()).mapToDouble(p -> p[0]).toArray();
        double mean = Arrays.stream(first).average().orElseThrow();
        double sd = Math.sqrt(Arrays.stream(first).map(v -> (v - mean) * (v - mean)).sum() / (first.length - 1));
        assertThat(mean).isBetween(-100.0, 100.0);
        assertThat(sd).isBetween(2850.0, 3150.0);
    }

    @Test
    void aBiggerMonthlyCostNeverRaisesTheChanceOfReachingTheGoal() {
        double previous = 1.0;
        for (double cost : new double[] {0, 1000, 2500, 5000, 8000, 12000}) {
            MonteCarloSimulator.Paths run = MonteCarloSimulator.simulate(inputs(SWINGS, constant(120, -cost), 99, 150000.0));
            double p = MonteCarloSimulator.reachedBy(run.reachMonth(), 18);
            assertThat(p).isLessThanOrEqualTo(previous);
            previous = p;
        }
        assertThat(previous).isZero();
    }

    @Test
    void reachQuantileIsNullWhenThatShareOfPathsNeverGetsThere() {
        assertThat(MonteCarloSimulator.reachQuantile(new int[] {3, 4, 0, 0}, 0.1)).isEqualTo(3);
        assertThat(MonteCarloSimulator.reachQuantile(new int[] {3, 4, 0, 0}, 0.9)).isNull();
        assertThat(MonteCarloSimulator.reachedBy(new int[] {3, 4, 0, 0}, 3)).isEqualTo(0.25);
    }
}
