package com.flowsight.dto.analytics;

import com.flowsight.ml.forecast.ForecastEvidence;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AnalyticsTrendResponse {
    private List<MonthlyTrendPoint> points;
    // how the projected points were made
    private Forecast forecast;

    public record Forecast(LocalDate asOf, int dataMonths, boolean lowHistory, String source,
                           ForecastEvidence spend, ForecastEvidence income, ForecastEvidence net) {}
}
