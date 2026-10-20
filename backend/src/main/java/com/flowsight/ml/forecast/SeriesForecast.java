package com.flowsight.ml.forecast;

import java.util.List;

/**
 * Forecast of one monthly series. Step k = 1 is the current calendar month.
 *
 * @param backtestPoints  (origin, horizon) pairs in the user's own rolling-origin backtest; 0 below six months
 * @param backtestMae     mean absolute error of that backtest, null when there was none
 * @param backtestWape    its absolute error over the actual totals, null when there was none
 * @param intervalRatio   user factor applied to the global error quantiles (1 = global prior only)
 * @param ownWeight       weight of the user's own errors in that factor, n / (n + 6)
 */
public record SeriesForecast(
    String name, SeriesKind kind, SeriesForecaster.Method method, int dataMonths, double scale, List<Step> steps,
    int backtestPoints, Double backtestMae, Double backtestWape, double intervalRatio, double ownWeight
) {
    public record Step(int k, double point, double lower80, double upper80, double lower95, double upper95) {}

    public Step step(int k) {
        return steps.get(k - 1);
    }
}
