package com.flowsight.dto.analytics;

import com.flowsight.ml.forecast.ForecastInterval;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthlyTrendPoint {
    private String month;        // "2024-01"
    private String label;        // "Jan 2024"
    private BigDecimal spend;
    private BigDecimal income;
    private BigDecimal net;
    private boolean projected;   // true = forecast, rendered with dashed line on frontend
    // prediction intervals of a projected point; null on actual months
    private Range range;

    public record Range(ForecastInterval spend, ForecastInterval income, ForecastInterval net) {}
}
