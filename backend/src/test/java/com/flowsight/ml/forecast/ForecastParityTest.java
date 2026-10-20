package com.flowsight.ml.forecast;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Expected values come from ml-service/app/forecaster/model.py on the same history and priors. */
class ForecastParityTest {

    private static final double EPS = 1e-6;

    @Test
    void inAppForecastMatchesTheMlServiceModel() {
        List<MonthlyHistory.Txn> txns = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate d = LocalDate.of(2025, 8, 1).plusMonths(i);
            txns.add(new MonthlyHistory.Txn(d, TransactionType.CREDIT, TransactionCategory.INCOME, 60000.0 + (i % 5 == 0 ? 2000.0 : 0.0)));
            txns.add(new MonthlyHistory.Txn(d.withDayOfMonth(3), TransactionType.DEBIT, TransactionCategory.GROCERIES, 8000.0 + 150.0 * i));
            txns.add(new MonthlyHistory.Txn(d.withDayOfMonth(20), TransactionType.DEBIT, TransactionCategory.SHOPPING,
                i % 12 == 3 ? 30000.0 : 5000.0 + 37.0 * i));
        }
        txns.add(new MonthlyHistory.Txn(LocalDate.of(2026, 10, 2), TransactionType.CREDIT, TransactionCategory.INCOME, 61000.0));
        txns.add(new MonthlyHistory.Txn(LocalDate.of(2026, 10, 4), TransactionType.DEBIT, TransactionCategory.GROCERIES, 3000.0));
        MonthlyHistory h = MonthlyHistory.of(txns, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 10, 9));
        CashFlowForecast f = CashFlowForecaster.forecast(h, 4, ForecastPriors.bundled());

        assertThat(h.dataMonths()).isEqualTo(14);
        check(f.income(), SeriesForecaster.Method.RECENT_MEDIAN, 60333.333333333336, 0.16666666666666666,
            new double[] {60000.0, 59077.362555555555, 63039.744166666664, 57569.55211111111, 63968.10322222222},
            new double[] {60000.0, 59125.78005555556, 63302.29472222222, 57589.81405555556, 64041.800388888885});
        check(f.spend(), SeriesForecaster.Method.TREND, 16476.583333333332, 0.39047489058275214,
            new double[] {15580.6, 14474.463040607683, 19677.36955205167, 13344.18246141122, 26086.987985319407},
            new double[] {15700.28, 14524.789486970456, 19710.73492794525, 13276.88729513421, 25849.468356972397});
        check(f.net(), SeriesForecaster.Method.INCOME_MINUS_SPEND, 76809.91666666667, 0.24315655859559376,
            new double[] {44419.4, 39360.63228456511, 47638.65640338128, 30553.236200071893, 50730.64329661948},
            new double[] {44299.72, 38833.40354705866, 47530.83175537723, 30506.64270383275, 51110.35995237695});
        check(f.category("GROCERIES"), SeriesForecaster.Method.TREND, 9125.0, 0.723849099681766,
            new double[] {10070.0, 8918.137881343333, 11320.809685784767, 7147.615779021298, 13917.456476474053},
            new double[] {10166.0, 9024.698424374988, 11419.698115698344, 7254.547985375698, 14049.466148004982});
        check(f.category("SHOPPING"), SeriesForecaster.Method.TREND, 7351.583333333333, 0.29789164711948934,
            new double[] {5510.6, 5135.435423927966, 5917.992063610443, 4558.771634759811, 6763.726875667513},
            new double[] {5534.28, 5160.292117134613, 5945.098589981865, 4580.239632170646, 6806.835224570175});
    }

    private static void check(SeriesForecast s, SeriesForecaster.Method method, double scale, double ratio,
                              double[] step1, double[] step2) {
        assertThat(s.method()).isEqualTo(method);
        assertThat(s.scale()).isCloseTo(scale, within(EPS));
        assertThat(s.intervalRatio()).isCloseTo(ratio, within(EPS));
        assertThat(s.backtestPoints()).isEqualTo(30);
        double[][] expected = {step1, step2};
        for (int k = 1; k <= 2; k++) {
            SeriesForecast.Step st = s.step(k);
            double[] got = {st.point(), st.lower80(), st.upper80(), st.lower95(), st.upper95()};
            for (int i = 0; i < got.length; i++) assertThat(got[i]).isCloseTo(expected[k - 1][i], within(EPS));
        }
    }
}
