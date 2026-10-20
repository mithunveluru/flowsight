package com.flowsight.ml.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/v1/forecast. Each series has one point per month, starting with the current month
 * (partial = true: its actual total is still accumulating) and then {@code months} future months.
 */
public record ForecastResponse(
    LocalDate asOf, int dataMonths, boolean lowHistory, String source,
    Series income, Series spend, Series net, List<Series> categories
) {
    public record Point(String month, boolean partial, BigDecimal point, ForecastInterval interval) {}

    public record Series(String series, String label, List<Point> points, ForecastEvidence evidence) {}

    public static ForecastResponse of(CashFlowForecast f, MonthlyHistory h) {
        List<Series> categories = new ArrayList<>();
        for (SeriesForecast c : f.categories()) {
            categories.add(series(f, c, h, com.flowsight.entity.TransactionCategory.valueOf(c.name()).getDisplayName()));
        }
        return new ForecastResponse(f.asOf(), f.dataMonths(), f.dataMonths() <= ForecastEvidence.LOW_HISTORY_MONTHS,
            f.source(), series(f, f.income(), h, "Income"), series(f, f.spend(), h, "Spending"),
            series(f, f.net(), h, "Net cash flow"), List.copyOf(categories));
    }

    static Series series(CashFlowForecast f, SeriesForecast s, MonthlyHistory h, String label) {
        List<Point> points = new ArrayList<>();
        for (SeriesForecast.Step st : s.steps()) {
            points.add(new Point(f.month(st.k()).toString(), st.k() == 1, ForecastInterval.money(st.point()),
                ForecastInterval.of(st)));
        }
        return new Series(s.name(), label, List.copyOf(points), ForecastEvidence.of(f, s, h));
    }
}
