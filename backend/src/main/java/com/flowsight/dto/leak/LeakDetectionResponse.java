package com.flowsight.dto.leak;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LeakDetectionResponse {
    private int        totalLeaksFound;
    private BigDecimal totalMonthlyImpact;
    private BigDecimal totalAnnualImpact;
    private List<LeakInsight> leaks;
}
