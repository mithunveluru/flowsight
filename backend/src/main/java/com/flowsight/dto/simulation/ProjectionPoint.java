package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// One month of the projection timeline; cumulativeSavings is the net position at month end.
@Data
@Builder
public class ProjectionPoint {
    private int        month;              // 1..N
    private BigDecimal cumulativeSavings;
    private BigDecimal monthlyNet;         // income - spend - recurring - scenarioCost
}
