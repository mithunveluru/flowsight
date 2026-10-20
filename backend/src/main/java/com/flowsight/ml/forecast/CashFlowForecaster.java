package com.flowsight.ml.forecast;

import com.flowsight.entity.TransactionCategory;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Forecasts every series of a {@link MonthlyHistory}; the in-app path that works without ml-service. */
public final class CashFlowForecaster {

    // Errors are scaled by at least 2% of the user's monthly income plus spend. With little history a
    // series' own month is too noisy a yardstick: income and spend use the whole monthly flow with no
    // complete month (extrapolated from the month so far) and a quarter of it with one.
    static final double FLOOR_SHARE = 0.02;
    static final double NO_HISTORY_FLOOR_SHARE = 1.0;
    static final double ONE_MONTH_FLOOR_SHARE = 0.25;

    private CashFlowForecaster() {}

    public static CashFlowForecast forecast(MonthlyHistory h, int steps, ForecastPriors priors) {
        double floor = floor(h, SeriesKind.CATEGORY);
        double totalsFloor = floor(h, SeriesKind.INCOME);
        SeriesForecast income = SeriesForecaster.forecast("income", SeriesKind.INCOME, h.income(), h.incomeToDate(),
            h.incomeSameDays(), steps, totalsFloor, priors);
        SeriesForecast spend = SeriesForecaster.forecast("spend", SeriesKind.SPEND, h.spend(), h.spendToDate(),
            h.spendSameDays(), steps, totalsFloor, priors);
        SeriesForecast net = SeriesForecaster.net(income, spend, h.income(), h.spend(), floor, priors);
        List<SeriesForecast> categories = new ArrayList<>();
        for (Map.Entry<TransactionCategory, double[]> e : h.categories().entrySet()) {
            TransactionCategory c = e.getKey();
            categories.add(SeriesForecaster.forecast(c.name(), SeriesKind.CATEGORY, e.getValue(),
                h.categoriesToDate().getOrDefault(c, 0.0), h.categoriesSameDays().getOrDefault(c, 0.0),
                steps, floor, priors));
        }
        return new CashFlowForecast(h.today(), YearMonth.from(h.today()), h.dataMonths(), priors.version(),
            CashFlowForecast.IN_APP, null, income, spend, net, List.copyOf(categories));
    }

    static double floor(MonthlyHistory h, SeriesKind kind) {
        int n = h.dataMonths();
        double level;
        if (n > 0) {
            int from = Math.max(0, n - 12);
            level = (SeriesForecaster.sum(h.income(), from, n) + SeriesForecaster.sum(h.spend(), from, n)) / (n - from);
        } else {
            level = (h.incomeToDate() + h.spendToDate()) / Math.max(h.dayFraction(), BudgetPace.MIN_ELAPSED_SHARE);
        }
        double share = FLOOR_SHARE;
        if ((kind == SeriesKind.INCOME || kind == SeriesKind.SPEND) && n <= 1) {
            share = n == 0 ? NO_HISTORY_FLOOR_SHARE : ONE_MONTH_FLOOR_SHARE;
        }
        return Math.max(1.0, share * level);
    }
}
