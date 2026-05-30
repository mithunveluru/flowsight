package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyticsTrendResponse {
    private List<MonthlyTrendPoint> points;
}
