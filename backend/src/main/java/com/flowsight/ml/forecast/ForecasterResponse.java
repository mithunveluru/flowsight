package com.flowsight.ml.forecast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The result object of ml-service's forecaster. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecasterResponse(@JsonProperty("priors_version") String priorsVersion, List<Series> series) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Series(
        String name, String kind, String method,
        @JsonProperty("data_months") int dataMonths, double scale, List<Step> steps,
        @JsonProperty("backtest_points") int backtestPoints,
        @JsonProperty("backtest_mae") Double backtestMae,
        @JsonProperty("backtest_wape") Double backtestWape,
        @JsonProperty("interval_ratio") double intervalRatio,
        @JsonProperty("own_weight") double ownWeight
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(int k, double point, double lower80, double upper80, double lower95, double upper95) {}

    /**
     * The forecast for the request built from {@code h}, or null when the answer does not match it
     * (missing series, wrong step count, non-finite or inverted intervals); callers then use the in-app path.
     */
    public CashFlowForecast toForecast(ForecasterRequest request, MonthlyHistory h, String modelVersion) {
        if (series == null) return null;
        Map<String, SeriesForecast> byName = new HashMap<>();
        for (Series s : series) {
            SeriesForecast f = s == null ? null : convert(s, request.steps(), h.dataMonths());
            if (f == null) return null;
            byName.put(f.name(), f);
        }
        SeriesForecast income = byName.get("income");
        SeriesForecast spend = byName.get("spend");
        SeriesForecast net = byName.get("net");
        if (income == null || spend == null || net == null) return null;
        List<SeriesForecast> categories = new ArrayList<>();
        for (ForecasterRequest.Series s : request.series().subList(2, request.series().size())) {
            SeriesForecast c = byName.get(s.name());
            if (c == null || c.kind() != SeriesKind.CATEGORY) return null;
            categories.add(c);
        }
        return new CashFlowForecast(h.today(), YearMonth.from(h.today()), h.dataMonths(), priorsVersion,
            CashFlowForecast.ML_SERVICE, modelVersion, income, spend, net, List.copyOf(categories));
    }

    private static SeriesForecast convert(Series s, int steps, int dataMonths) {
        SeriesKind kind = kind(s.kind());
        SeriesForecaster.Method method = method(s.method());
        if (kind == null || method == null || s.name() == null || s.steps() == null || s.steps().size() != steps
            || s.dataMonths() != dataMonths || !Double.isFinite(s.scale()) || !Double.isFinite(s.intervalRatio())) {
            return null;
        }
        List<SeriesForecast.Step> out = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            Step st = s.steps().get(i);
            if (st == null || st.k() != i + 1) return null;
            double[] v = {st.point(), st.lower80(), st.upper80(), st.lower95(), st.upper95()};
            for (double x : v) if (!Double.isFinite(x)) return null;
            if (st.lower95() > st.lower80() || st.lower80() > st.upper80() || st.upper80() > st.upper95()) return null;
            out.add(new SeriesForecast.Step(st.k(), st.point(), st.lower80(), st.upper80(), st.lower95(), st.upper95()));
        }
        return new SeriesForecast(s.name(), kind, method, s.dataMonths(), s.scale(), List.copyOf(out),
            s.backtestPoints(), s.backtestMae(), s.backtestWape(), s.intervalRatio(), s.ownWeight());
    }

    private static SeriesKind kind(String id) {
        for (SeriesKind k : SeriesKind.values()) if (k.id().equals(id)) return k;
        return null;
    }

    private static SeriesForecaster.Method method(String id) {
        for (SeriesForecaster.Method m : SeriesForecaster.Method.values()) if (m.id().equals(id)) return m;
        return null;
    }
}
