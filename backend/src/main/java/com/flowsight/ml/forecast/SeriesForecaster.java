package com.flowsight.ml.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Monthly forecast of one series with prediction intervals. Mirrors ml-service/app/forecaster/model.py;
 * the eval backtest runs both on the same histories.
 *
 * <p>Step k is k months after the last complete month (k = 1 is the current calendar month).
 * With six or more complete months the point method is chosen per series by a rolling-origin
 * backtest on the user's own history (recent mean, recent median, exponential smoothing,
 * last year's seasonal deviation, damped robust trend). Shorter histories use the recent mean,
 * blended with the month so far when the user's own day-of-month profile makes it informative;
 * with no complete month the month-to-date total is the forecast.
 *
 * <p>Intervals are global quantiles of scaled errors (calibrated offline on synthetic histories,
 * see {@link ForecastPriors}) times a user factor: the median scaled error of the user's own
 * backtest, shrunk toward the global median with {@value #SHRINK_PSEUDO_COUNT} pseudo-observations.
 */
public final class SeriesForecaster {

    static final double SHRINK_PSEUDO_COUNT = 6.0;
    static final int MIN_SELECTION_MONTHS = 6;
    static final int SELECTION_ORIGINS = 9;
    static final double MIN_PROFILE_SHARE = 0.2;
    static final double SES_ALPHA = 0.3;
    static final double TREND_DAMPING = 0.8;

    public enum Method {
        RECENT_MEAN("recent-mean"),
        RECENT_MEDIAN("recent-median"),
        SMOOTHED("exponential-smoothing"),
        SEASONAL("seasonal-deviation"),
        TREND("damped-trend"),
        RECENT_MEAN_WITH_MONTH_TO_DATE("recent-mean+month-to-date"),
        MONTH_TO_DATE("month-to-date"),
        INCOME_MINUS_SPEND("income-minus-spend");

        private final String id;

        Method(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private static final Method[] CANDIDATES = {
        Method.RECENT_MEAN, Method.RECENT_MEDIAN, Method.SMOOTHED, Method.SEASONAL, Method.TREND
    };

    private SeriesForecaster() {}

    /**
     * @param y        complete monthly totals, oldest first (at most 24)
     * @param monthToDate total so far in the current month
     * @param sameDays total of the complete months up to the current day of month (day-of-month profile)
     * @param steps    number of months to forecast, starting with the current month
     * @param floor    smallest scale for errors (keeps tiny series from producing absurd ratios)
     */
    public static SeriesForecast forecast(String name, SeriesKind kind, double[] y, double monthToDate, double sameDays,
                                          int steps, double floor, ForecastPriors priors) {
        int n = y.length;
        Method method;
        double[] point = new double[steps];
        double scale;
        double[] residuals = new double[0];
        double backtestAbs = 0;
        double backtestActual = 0;

        if (n == 0) {
            method = Method.MONTH_TO_DATE;
            Arrays.fill(point, monthToDate);
            scale = Math.max(monthToDate, floor);
        } else if (n < MIN_SELECTION_MONTHS) {
            double total = sum(y, 0, n);
            double share = total > 0 ? sameDays / total : 0.0;
            int from = Math.max(0, n - 3);
            double recent = sum(y, from, n);
            int count = n - from;
            if (share >= MIN_PROFILE_SHARE) {
                method = Method.RECENT_MEAN_WITH_MONTH_TO_DATE;
                double estimate = monthToDate / share;
                double weight = Math.min(1.0, share);
                Arrays.fill(point, (recent + weight * estimate) / (count + weight));
                scale = Math.max((recent + estimate) / (count + 1), floor);
            } else {
                method = Method.RECENT_MEAN;
                Arrays.fill(point, recent / count);
                scale = Math.max(meanAbsRecent(y, n), floor);
            }
        } else {
            method = select(y, steps);
            for (int k = 1; k <= steps; k++) point[k - 1] = predict(method, y, n, k);
            scale = Math.max(meanAbsRecent(y, n), floor);
            List<double[]> errors = backtest(method, y, steps);
            residuals = new double[errors.size()];
            for (int i = 0; i < residuals.length; i++) {
                double[] e = errors.get(i);
                residuals[i] = (e[0] - e[1]) / scale;
                backtestAbs += Math.abs(e[0] - e[1]);
                backtestActual += Math.abs(e[0]);
            }
        }
        return withIntervals(name, kind, method, n, scale, point, residuals, backtestAbs, backtestActual, priors);
    }

    /** Net cash flow as income minus spend, with intervals from the joint backtest of both. */
    public static SeriesForecast net(SeriesForecast income, SeriesForecast spend, double[] incomeY, double[] spendY,
                                     double floor, ForecastPriors priors) {
        int steps = income.steps().size();
        int n = incomeY.length;
        double[] point = new double[steps];
        for (int k = 0; k < steps; k++) point[k] = income.steps().get(k).point() - spend.steps().get(k).point();
        double scale = Math.max(income.scale() + spend.scale(), floor);
        double[] residuals = new double[0];
        double backtestAbs = 0;
        double backtestActual = 0;
        if (n >= MIN_SELECTION_MONTHS) {
            Method mi = select(incomeY, steps);
            Method ms = select(spendY, steps);
            List<Double> res = new ArrayList<>();
            for (int j = Math.max(3, n - SELECTION_ORIGINS); j < n; j++) {
                for (int k = 1; k <= steps && j - 1 + k < n; k++) {
                    double actual = incomeY[j - 1 + k] - spendY[j - 1 + k];
                    double f = predict(mi, incomeY, j, k) - predict(ms, spendY, j, k);
                    res.add((actual - f) / scale);
                    backtestAbs += Math.abs(actual - f);
                    backtestActual += Math.abs(incomeY[j - 1 + k]) + Math.abs(spendY[j - 1 + k]);
                }
            }
            residuals = res.stream().mapToDouble(Double::doubleValue).toArray();
        }
        return withIntervals("net", SeriesKind.NET, Method.INCOME_MINUS_SPEND, n, scale, point, residuals,
            backtestAbs, backtestActual, priors);
    }

    private static SeriesForecast withIntervals(String name, SeriesKind kind, Method method, int n, double scale,
                                                double[] point, double[] residuals, double backtestAbs,
                                                double backtestActual, ForecastPriors priors) {
        List<SeriesForecast.Step> out = new ArrayList<>();
        double ratio = 1.0;
        for (int k = 1; k <= point.length; k++) {
            ForecastPriors.Prior prior = priors.prior(kind, n, k);
            if (residuals.length > 0) {
                double own = median(absAll(residuals));
                ratio = ((residuals.length * own + SHRINK_PSEUDO_COUNT * prior.mad())
                    / (residuals.length + SHRINK_PSEUDO_COUNT)) / prior.mad();
            }
            double p = point[k - 1];
            double w = scale * ratio;
            double lo80 = p + w * prior.q10();
            double hi80 = p + w * prior.q90();
            double lo95 = p + w * prior.q025();
            double hi95 = p + w * prior.q975();
            if (kind != SeriesKind.NET) {
                lo80 = Math.max(0, lo80);
                lo95 = Math.max(0, lo95);
                hi80 = Math.max(0, hi80);
                hi95 = Math.max(0, hi95);
            }
            out.add(new SeriesForecast.Step(k, p, lo80, hi80, lo95, hi95));
        }
        int points = residuals.length;
        Double mae = points > 0 ? backtestAbs / points : null;
        Double wape = points > 0 && backtestActual > 0 ? backtestAbs / backtestActual : null;
        return new SeriesForecast(name, kind, method, n, scale, List.copyOf(out), points, mae, wape, ratio,
            points / (points + SHRINK_PSEUDO_COUNT));
    }

    static Method select(double[] y, int steps) {
        int n = y.length;
        double[] err = new double[CANDIDATES.length];
        int count = 0;
        for (int j = Math.max(3, n - SELECTION_ORIGINS); j < n; j++) {
            for (int k = 1; k <= steps && j - 1 + k < n; k++) {
                double actual = y[j - 1 + k];
                for (int m = 0; m < CANDIDATES.length; m++) err[m] += Math.abs(actual - predict(CANDIDATES[m], y, j, k));
                count++;
            }
        }
        if (count == 0) return Method.RECENT_MEAN;
        int best = 0;
        for (int m = 1; m < CANDIDATES.length; m++) {
            if (err[m] / count < err[best] / count) best = m;
        }
        return CANDIDATES[best];
    }

    // (actual, forecast) pairs of the rolling-origin backtest over the user's last months
    static List<double[]> backtest(Method method, double[] y, int steps) {
        int n = y.length;
        List<double[]> out = new ArrayList<>();
        for (int j = Math.max(3, n - SELECTION_ORIGINS); j < n; j++) {
            for (int k = 1; k <= steps && j - 1 + k < n; k++) {
                out.add(new double[] {y[j - 1 + k], predict(method, y, j, k)});
            }
        }
        return out;
    }

    // forecast k months after y[len - 1], fitted on y[0..len)
    static double predict(Method method, double[] y, int len, int k) {
        return switch (method) {
            case RECENT_MEAN -> sum(y, Math.max(0, len - 3), len) / Math.min(3, len);
            case RECENT_MEDIAN -> median(Arrays.copyOfRange(y, Math.max(0, len - 6), len));
            case SMOOTHED -> smoothed(y, len);
            case SEASONAL -> seasonal(y, len, k);
            case TREND -> trend(y, len, k);
            default -> throw new IllegalArgumentException(method.name());
        };
    }

    private static double smoothed(double[] y, int len) {
        double level = y[0];
        for (int i = 1; i < len; i++) level = SES_ALPHA * y[i] + (1 - SES_ALPHA) * level;
        return level;
    }

    // recent median plus a shrunk deviation of the same calendar month from its year's mean
    private static double seasonal(double[] y, int len, int k) {
        if (len < 13) return predict(Method.RECENT_MEDIAN, y, len, k);
        int t = len - 1 + k;
        double level = predict(Method.RECENT_MEDIAN, y, len, k);
        double devs = 0;
        int count = 0;
        if (t - 12 <= len - 1) {
            devs += y[t - 12] - sum(y, len - 12, len) / 12;
            count++;
        }
        if (len >= 24 && t - 24 >= 0) {
            devs += y[t - 24] - sum(y, len - 24, len - 12) / 12;
            count++;
        }
        if (count == 0) return level;
        double phi = count == 1 ? 0.5 : 0.7;
        return Math.max(0, level + phi * devs / count);
    }

    // Theil-Sen slope over the last 12 months, damped
    private static double trend(double[] y, int len, int k) {
        if (len < 9) return predict(Method.RECENT_MEDIAN, y, len, k);
        int from = Math.max(0, len - 12);
        int m = len - from;
        double[] slopes = new double[m * (m - 1) / 2];
        int s = 0;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) slopes[s++] = (y[from + j] - y[from + i]) / (j - i);
        }
        double b = median(slopes);
        double[] levels = new double[m];
        for (int i = 0; i < m; i++) levels[i] = y[from + i] - b * (i - (m - 1));
        double level = median(levels);
        double damp = 0;
        double phi = 1;
        for (int i = 1; i <= k; i++) {
            phi *= TREND_DAMPING;
            damp += phi;
        }
        return Math.max(0, level + b * damp);
    }

    private static double meanAbsRecent(double[] y, int n) {
        int from = Math.max(0, n - 12);
        double s = 0;
        for (int i = from; i < n; i++) s += Math.abs(y[i]);
        return s / (n - from);
    }

    static double sum(double[] y, int from, int to) {
        double s = 0;
        for (int i = from; i < to; i++) s += y[i];
        return s;
    }

    static double median(double[] values) {
        double[] v = values.clone();
        Arrays.sort(v);
        int n = v.length;
        return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2;
    }

    private static double[] absAll(double[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Math.abs(v[i]);
        return out;
    }
}
