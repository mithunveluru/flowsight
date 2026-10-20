package com.flowsight.ml.forecast;

import java.util.List;

/**
 * How a forecast was made, for the UI's evidence drawer. Accuracy fields come only from the user's
 * own rolling-origin backtest and are null when the history is too short to run one.
 *
 * @param window           first and last complete month used, null when there is none
 * @param intervalSource   GLOBAL_PRIOR (synthetic calibration only) or GLOBAL_PRIOR_SHRUNK_TO_OWN_ERRORS
 * @param ownWeight        weight of the user's own errors in the interval width, n / (n + 6)
 */
public record ForecastEvidence(
    String method, List<String> window, int dataMonths, boolean lowHistory, List<Integer> intervalLevels,
    String intervalSource, int backtestPoints, Double backtestMae, Double backtestWape, double ownWeight,
    String priorsVersion, String source, String modelVersion, String note
) {
    public static final int LOW_HISTORY_MONTHS = 3;

    public static ForecastEvidence of(CashFlowForecast f, SeriesForecast s, MonthlyHistory h) {
        List<String> window = h.months().isEmpty() ? null
            : List.of(h.months().get(0).toString(), h.months().get(h.months().size() - 1).toString());
        boolean low = s.dataMonths() <= LOW_HISTORY_MONTHS;
        String intervalSource = s.backtestPoints() > 0 ? "GLOBAL_PRIOR_SHRUNK_TO_OWN_ERRORS" : "GLOBAL_PRIOR";
        return new ForecastEvidence(s.method().id(), window, s.dataMonths(), low, List.of(80, 95), intervalSource,
            s.backtestPoints(), round(s.backtestMae()), round4(s.backtestWape()), round4(s.ownWeight()),
            f.priorsVersion(), f.source(), f.modelVersion(), note(s));
    }

    static String note(SeriesForecast s) {
        if (s.dataMonths() == 0) {
            return "No complete month yet: the forecast is this month's total so far, and the range comes "
                + "from how much months like this varied for other (synthetic) histories.";
        }
        if (s.backtestPoints() == 0) {
            return "Based on " + s.dataMonths() + " complete " + (s.dataMonths() == 1 ? "month" : "months")
                + ". Too little history to test the forecast on your own data, so the range is wide and "
                + "comes from a global calibration on synthetic histories.";
        }
        return "Method picked by replaying your last months: off by " + Math.round(s.backtestMae())
            + " a month on average in that replay. The range combines your own errors with a global calibration.";
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 100) / 100.0;
    }

    private static Double round4(Double v) {
        return v == null ? null : Math.round(v * 10000) / 10000.0;
    }
}
