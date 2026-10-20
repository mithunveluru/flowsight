package com.flowsight.ml.forecast;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeriesForecasterTest {

    private static final ForecastPriors PRIORS = ForecastPriors.bundled();

    private static SeriesForecast spend(double[] y, double toDate, double sameDays) {
        return SeriesForecaster.forecast("spend", SeriesKind.SPEND, y, toDate, sameDays, 4, 900, PRIORS);
    }

    @Test
    void withoutACompleteMonthTheMonthSoFarIsTheForecastAndTheRangeIsWide() {
        SeriesForecast f = spend(new double[0], 12000, 0);
        assertThat(f.method()).isEqualTo(SeriesForecaster.Method.MONTH_TO_DATE);
        assertThat(f.backtestPoints()).isZero();
        assertThat(f.backtestMae()).isNull();
        assertThat(f.step(2).point()).isEqualTo(12000);
        assertThat(f.step(2).upper95() - f.step(2).lower95()).isGreaterThan(12000);
    }

    @Test
    void shortHistoryBlendsTheMonthSoFarThroughTheUsersOwnDayProfile() {
        // half of past spend had happened by today, 6,000 so far: this month looks like 12,000
        SeriesForecast f = spend(new double[] {10000, 10000}, 6000, 10000);
        assertThat(f.method()).isEqualTo(SeriesForecaster.Method.RECENT_MEAN_WITH_MONTH_TO_DATE);
        assertThat(f.step(1).point()).isCloseTo((20000 + 0.5 * 12000) / 2.5, within(1e-9));

        SeriesForecast flat = spend(new double[] {10000, 11000, 12000}, 100, 100);
        assertThat(flat.method()).isEqualTo(SeriesForecaster.Method.RECENT_MEAN);
        assertThat(flat.step(3).point()).isEqualTo(11000);
    }

    @Test
    void backtestPicksTheSeasonalMethodForAYearlyPattern() {
        double[] y = IntStream.range(0, 24).mapToDouble(i -> i % 12 == 10 ? 30000 : 10000).toArray();
        SeriesForecast f = spend(y, 0, 0);
        assertThat(f.method()).isEqualTo(SeriesForecaster.Method.SEASONAL);
        // eleven months after the last one is the next spike month
        assertThat(SeriesForecaster.predict(SeriesForecaster.Method.SEASONAL, y, 24, 11)).isGreaterThan(20000);
    }

    @Test
    void backtestPicksTheTrendForASteadyRise() {
        double[] y = IntStream.range(0, 14).mapToDouble(i -> 10000 + 1000 * i).toArray();
        SeriesForecast f = spend(y, 0, 0);
        assertThat(f.method()).isEqualTo(SeriesForecaster.Method.TREND);
        assertThat(f.step(1).point()).isGreaterThan(y[13]);
        assertThat(f.backtestPoints()).isPositive();
        assertThat(f.backtestMae()).isNotNull();
    }

    @Test
    void intervalsAreOrderedNonNegativeAndNarrowerForAStableUser() {
        double[] stable = IntStream.range(0, 12).mapToDouble(i -> 20000 + (i % 2) * 200).toArray();
        double[] noisy = IntStream.range(0, 12).mapToDouble(i -> 20000 + (i % 2 == 0 ? 8000 : -8000)).toArray();
        SeriesForecast a = spend(stable, 0, 0);
        SeriesForecast b = spend(noisy, 0, 0);
        for (SeriesForecast f : new SeriesForecast[] {a, b}) {
            for (SeriesForecast.Step s : f.steps()) {
                assertThat(s.lower95()).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(s.lower80());
                assertThat(s.lower80()).isLessThanOrEqualTo(s.point());
                assertThat(s.point()).isLessThanOrEqualTo(s.upper80());
                assertThat(s.upper80()).isLessThanOrEqualTo(s.upper95());
            }
        }
        assertThat(a.intervalRatio()).isLessThan(b.intervalRatio());
        assertThat(a.step(2).upper80() - a.step(2).lower80()).isLessThan(b.step(2).upper80() - b.step(2).lower80());
    }

    @Test
    void netIsIncomeMinusSpendAndMayGoNegative() {
        double[] income = IntStream.range(0, 8).mapToDouble(i -> 30000).toArray();
        double[] spend = IntStream.range(0, 8).mapToDouble(i -> 29000 + (i % 2) * 6000).toArray();
        SeriesForecast in = SeriesForecaster.forecast("income", SeriesKind.INCOME, income, 0, 0, 4, 1000, PRIORS);
        SeriesForecast out = SeriesForecaster.forecast("spend", SeriesKind.SPEND, spend, 0, 0, 4, 1000, PRIORS);
        SeriesForecast net = SeriesForecaster.net(in, out, income, spend, 1000, PRIORS);
        assertThat(net.method()).isEqualTo(SeriesForecaster.Method.INCOME_MINUS_SPEND);
        assertThat(net.step(2).point()).isEqualTo(in.step(2).point() - out.step(2).point());
        assertThat(net.step(2).lower95()).isNegative();
    }
}
