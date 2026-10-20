package com.flowsight.ml.forecast;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Income, spend, net and per-category forecasts for one user. Step 1 is the current month.
 *
 * @param source       "ml-service" when the forecaster capability answered, otherwise "in-app"
 * @param modelVersion ml-service model version, null for the in-app path
 */
public record CashFlowForecast(
    LocalDate asOf, YearMonth firstMonth, int dataMonths, String priorsVersion, String source, String modelVersion,
    SeriesForecast income, SeriesForecast spend, SeriesForecast net, List<SeriesForecast> categories
) {
    public static final String IN_APP = "in-app";
    public static final String ML_SERVICE = "ml-service";

    public YearMonth month(int step) {
        return firstMonth.plusMonths(step - 1L);
    }

    public SeriesForecast category(String name) {
        return categories.stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }
}
