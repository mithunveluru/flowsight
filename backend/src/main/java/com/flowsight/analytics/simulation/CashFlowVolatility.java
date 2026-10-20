package com.flowsight.analytics.simulation;

import com.flowsight.ml.forecast.ForecastPriors;
import com.flowsight.ml.forecast.MonthlyHistory;
import com.flowsight.ml.forecast.SeriesKind;

import java.util.List;

/**
 * How much a user's monthly net cash flow swings: the standard deviation over their complete months,
 * shrunk toward the global prior (the forecaster's one-month-ahead net error at the same history
 * length) with six pseudo-months, and the deviations the Monte Carlo resamples, rescaled to it.
 */
public record CashFlowVolatility(int months, List<String> window, double[] residuals, Double ownSd, double priorSd,
                                 double sd, double ownWeight, String source) {

    static final double PSEUDO_MONTHS = 6.0;
    static final double MAD_TO_SD = 1.4826;
    static final int OWN_HISTORY_MONTHS = 12;

    public static CashFlowVolatility of(MonthlyHistory h, ForecastPriors priors) {
        double[] net = h.net();
        int m = net.length;
        List<String> window = m == 0 ? null
            : List.of(h.months().get(0).toString(), h.months().get(m - 1).toString());
        double level = 0;
        if (m > 0) {
            int from = Math.max(0, m - 12);
            for (int i = from; i < m; i++) level += h.income()[i] + h.spend()[i];
            level /= m - from;
        }
        double priorSd = MAD_TO_SD * priors.prior(SeriesKind.NET, m, 1).mad() * Math.max(level, 1.0);
        if (m < 2) return new CashFlowVolatility(m, window, new double[0], null, priorSd, priorSd, 0, "PRIOR");

        double mean = 0;
        for (double v : net) mean += v;
        mean /= m;
        double ss = 0;
        for (double v : net) ss += (v - mean) * (v - mean);
        double own = Math.sqrt(ss / (m - 1));
        double sd = Math.sqrt((m * own * own + PSEUDO_MONTHS * priorSd * priorSd) / (m + PSEUDO_MONTHS));
        double weight = m / (m + PSEUDO_MONTHS);
        double[] residuals = new double[0];
        if (m >= MonteCarloSimulator.MIN_BOOTSTRAP_MONTHS && own > 0) {
            residuals = new double[m];
            for (int i = 0; i < m; i++) residuals[i] = (net[i] - mean) * sd / own;
        }
        String source = residuals.length == 0 ? "PRIOR" : m >= OWN_HISTORY_MONTHS ? "OWN_HISTORY" : "SHRUNK_TO_PRIOR";
        return new CashFlowVolatility(m, window, residuals, own, priorSd, sd, weight, source);
    }
}
