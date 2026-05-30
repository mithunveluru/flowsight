package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlexibilityScore {
    private int     currentScore;       // 0-100
    private int     projectedScore;     // 0-100 after scenario
    private double  deltaPercent;       // (projected - current) / current * 100
    private String  currentTier;        // EXCELLENT | GOOD | FAIR | TIGHT | CONSTRAINED
    private String  projectedTier;
    private String  explanation;
}
