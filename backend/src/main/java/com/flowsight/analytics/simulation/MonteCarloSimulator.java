package com.flowsight.analytics.simulation;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Monte Carlo paths of cumulative savings. Each month is the baseline monthly net plus a residual
 * plus the scenario's change for that month. Residuals are drawn as moving blocks of the user's own
 * monthly deviations (so a run of bad months stays a run); with too few months of history they are
 * normal with the given standard deviation instead. Baseline and scenario paths share the same draws,
 * so their difference is the scenario alone. Deterministic for a given seed.
 */
public final class MonteCarloSimulator {

    public static final int MIN_BOOTSTRAP_MONTHS = 3;
    public static final int BLOCK_LENGTH         = 3;

    /**
     * @param residuals    monthly deviations to resample, already scaled to the volatility to simulate
     * @param fallbackSd   standard deviation of normal residuals when residuals has fewer than 3 months
     * @param deltas       scenario change to the monthly net for months 1..horizon (index 0 = month 1)
     * @param goalRemaining amount still needed for the goal, or null without a goal
     */
    public record Inputs(double baseMonthlyNet, double[] residuals, double fallbackSd, double[] deltas,
                         int paths, long seed, Double goalRemaining) {}

    /**
     * cumulative[p][m] and baselineCumulative[p][m] are the savings after month m + 1 on path p;
     * reachMonth[p] is the first month the goal is covered (0 = not within the horizon).
     */
    public record Paths(double[][] cumulative, double[][] baselineCumulative, int[] reachMonth, int[] baselineReachMonth) {
        public int horizon() {
            return cumulative.length == 0 ? 0 : cumulative[0].length;
        }
    }

    private MonteCarloSimulator() {}

    public static Paths simulate(Inputs in) {
        int horizon = in.deltas().length;
        SplittableRandom rng = new SplittableRandom(in.seed());
        double[][] cum = new double[in.paths()][horizon];
        double[][] base = new double[in.paths()][horizon];
        int[] reach = new int[in.paths()];
        int[] baseReach = new int[in.paths()];
        boolean bootstrap = in.residuals().length >= MIN_BOOTSTRAP_MONTHS;
        double[] noise = new double[horizon];

        for (int p = 0; p < in.paths(); p++) {
            if (bootstrap) {
                blockBootstrap(in.residuals(), noise, rng);
            } else {
                for (int m = 0; m < horizon; m++) noise[m] = in.fallbackSd() * gaussian(rng);
            }
            double s = 0;
            double b = 0;
            for (int m = 0; m < horizon; m++) {
                double month = in.baseMonthlyNet() + noise[m];
                b += month;
                s += month + in.deltas()[m];
                base[p][m] = b;
                cum[p][m] = s;
                if (in.goalRemaining() != null) {
                    if (reach[p] == 0 && s >= in.goalRemaining()) reach[p] = m + 1;
                    if (baseReach[p] == 0 && b >= in.goalRemaining()) baseReach[p] = m + 1;
                }
            }
        }
        return new Paths(cum, base, reach, baseReach);
    }

    // moving blocks of BLOCK_LENGTH consecutive months, wrapping around the end of the history
    private static void blockBootstrap(double[] residuals, double[] out, SplittableRandom rng) {
        int n = residuals.length;
        int block = Math.min(BLOCK_LENGTH, n);
        int m = 0;
        while (m < out.length) {
            int start = rng.nextInt(n);
            for (int i = 0; i < block && m < out.length; i++, m++) out[m] = residuals[(start + i) % n];
        }
    }

    private static double gaussian(SplittableRandom rng) {
        double u1 = rng.nextDouble();
        double u2 = rng.nextDouble();
        return Math.sqrt(-2 * Math.log(1 - u1)) * Math.cos(2 * Math.PI * u2);
    }

    /** Linear-interpolated quantile of an unsorted sample (it is sorted in place). */
    public static double quantile(double[] values, double q) {
        Arrays.sort(values);
        double pos = q * (values.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(lo + 1, values.length - 1);
        return values[lo] + (values[hi] - values[lo]) * (pos - lo);
    }

    /** Share of paths that reached the goal by the given month. */
    public static double reachedBy(int[] reachMonth, int month) {
        if (reachMonth.length == 0) return 0;
        int hit = 0;
        for (int r : reachMonth) if (r > 0 && r <= month) hit++;
        return (double) hit / reachMonth.length;
    }

    /** Reach month at quantile q over all paths, null when that path has not reached it within the horizon. */
    public static Integer reachQuantile(int[] reachMonth, double q) {
        int[] sorted = Arrays.stream(reachMonth).map(r -> r == 0 ? Integer.MAX_VALUE : r).sorted().toArray();
        int v = sorted[(int) Math.min(sorted.length - 1, Math.floor(q * sorted.length))];
        return v == Integer.MAX_VALUE ? null : v;
    }
}
