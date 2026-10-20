package com.flowsight.ml.forecast;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BudgetPaceTest {

    private static SeriesForecast forecast(double point, double lo80, double hi80) {
        return new SeriesForecast("GROCERIES", SeriesKind.CATEGORY, SeriesForecaster.Method.RECENT_MEAN, 3, point,
            List.of(new SeriesForecast.Step(1, point, lo80, hi80, lo80 / 2, hi80 * 2)), 0, null, null, 1, 0);
    }

    @Test
    void addsTheForecastShareOfTheMonthStillAhead() {
        // rent-like spend: 90% of past months' spend had happened by today
        BudgetPace.Projection p = BudgetPace.project(forecast(10000, 8000, 12000),
            new double[] {10000, 10000, 10000}, 9500, 27000, 0.2);
        assertThat(p.method()).isEqualTo("forecast-remaining");
        assertThat(p.remainingShare()).isCloseTo(0.1, within(1e-9));
        assertThat(p.point()).isCloseTo(10500, within(1e-6));
        // the month's full interval width around the point, never below what is spent
        assertThat(p.lower80()).isCloseTo(9500, within(1e-6));
        assertThat(p.upper80()).isCloseTo(12500, within(1e-6));
        assertThat(p.upper95()).isCloseTo(24500, within(1e-6));
    }

    @Test
    void linearPaceWouldHaveTripledThatSameMonth() {
        double linear = 9500 / 0.2;
        BudgetPace.Projection p = BudgetPace.project(forecast(10000, 8000, 12000),
            new double[] {10000, 10000, 10000}, 9500, 27000, 0.2);
        assertThat(p.point()).isLessThan(linear / 4);
    }

    @Test
    void withoutHistoryTheMonthSoFarIsExtrapolatedWithTheElapsedShareFloored() {
        BudgetPace.Projection p = BudgetPace.project(forecast(300, 0, 900), new double[0], 300, 0, 0.1);
        assertThat(p.method()).isEqualTo("month-to-date-pace");
        assertThat(p.point()).isCloseTo(300 / 0.35, within(1e-9));
        assertThat(p.lower80()).isEqualTo(300);
        assertThat(p.lower95()).isEqualTo(300);
        assertThat(p.upper80()).isCloseTo(300 / 0.35 + 600, within(1e-9));
    }

    @Test
    void aSeriesWithNoForecastStaysAtWhatWasSpent() {
        BudgetPace.Projection p = BudgetPace.project(null, new double[0], 0, 0, 0.5);
        assertThat(p.point()).isZero();
        assertThat(p.method()).isEqualTo("none");
    }
}
