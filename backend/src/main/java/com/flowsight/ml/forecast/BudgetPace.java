package com.flowsight.ml.forecast;

/**
 * Month-end spend of one series: what is already spent plus the forecast's share of the month
 * that is still to come. The share comes from the user's own day-of-month profile (how much of
 * past months' spend had happened by today's day); with no complete month it falls back to the
 * days left, and the month so far is extrapolated with the elapsed share floored at 35%.
 *
 * <p>The interval keeps the full width of the month's forecast interval around that point (never
 * below what is already spent): narrowing it with the share of the month left covered 55% / 80%
 * instead of 80% / 95% on the synthetic calibration histories, because large purchases land on any day.
 */
public final class BudgetPace {

    static final double MIN_ELAPSED_SHARE = 0.35;

    public record Projection(String method, double remainingShare, double point,
                             double lower80, double upper80, double lower95, double upper95) {}

    private BudgetPace() {}

    /**
     * @param forecast    the series' forecast, step 1 = current month; null when the series has no history
     * @param history     complete months of the same series, oldest first
     * @param spentToDate this month's spend so far
     * @param sameDays    the complete months' spend up to today's day of month
     */
    public static Projection project(SeriesForecast forecast, double[] history, double spentToDate, double sameDays,
                                     double dayFraction) {
        if (forecast == null) return new Projection("none", 0, spentToDate, spentToDate, spentToDate, spentToDate, spentToDate);
        SeriesForecast.Step s = forecast.step(1);
        if (history.length == 0) {
            // the extrapolation is a guess this early, so the range starts at what is spent
            double point = Math.max(spentToDate, spentToDate / Math.max(dayFraction, MIN_ELAPSED_SHARE));
            return new Projection("month-to-date-pace", 1 - dayFraction, point,
                spentToDate, point + s.upper80() - s.point(), spentToDate, point + s.upper95() - s.point());
        }
        double total = SeriesForecaster.sum(history, 0, history.length);
        double elapsed = total > 0 ? sameDays / total : dayFraction;
        double remaining = Math.max(0, Math.min(1, 1 - elapsed));
        return around("forecast-remaining", remaining, spentToDate + s.point() * remaining, s, spentToDate);
    }

    private static Projection around(String method, double remaining, double point, SeriesForecast.Step s, double spent) {
        return new Projection(method, remaining, point,
            Math.max(spent, point + s.lower80() - s.point()), point + s.upper80() - s.point(),
            Math.max(spent, point + s.lower95() - s.point()), point + s.upper95() - s.point());
    }
}
