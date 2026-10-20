package com.flowsight.ml.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowsight.entity.TransactionCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Body of POST /v1/capabilities/forecaster/predict: monthly totals only, no transaction text. */
public record ForecasterRequest(int steps, @JsonProperty("day_fraction") double dayFraction, List<Series> series) {

    public record Series(
        String name, String kind, double[] values,
        @JsonProperty("month_to_date") double monthToDate,
        @JsonProperty("same_days") double sameDays
    ) {}

    public static ForecasterRequest of(MonthlyHistory h, int steps) {
        List<Series> series = new ArrayList<>();
        series.add(new Series("income", SeriesKind.INCOME.id(), h.income(), h.incomeToDate(), h.incomeSameDays()));
        series.add(new Series("spend", SeriesKind.SPEND.id(), h.spend(), h.spendToDate(), h.spendSameDays()));
        for (Map.Entry<TransactionCategory, double[]> e : h.categories().entrySet()) {
            TransactionCategory c = e.getKey();
            series.add(new Series(c.name(), SeriesKind.CATEGORY.id(), e.getValue(),
                h.categoriesToDate().getOrDefault(c, 0.0), h.categoriesSameDays().getOrDefault(c, 0.0)));
        }
        return new ForecasterRequest(steps, h.dayFraction(), List.copyOf(series));
    }
}
