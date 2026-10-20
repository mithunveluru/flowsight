package com.flowsight.ml.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 80% and 95% prediction interval around a forecast point. */
public record ForecastInterval(BigDecimal lower80, BigDecimal upper80, BigDecimal lower95, BigDecimal upper95) {

    public static ForecastInterval of(SeriesForecast.Step s) {
        return new ForecastInterval(money(s.lower80()), money(s.upper80()), money(s.lower95()), money(s.upper95()));
    }

    static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
