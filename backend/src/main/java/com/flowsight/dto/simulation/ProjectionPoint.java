package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One month of the projection timeline.
 *
 * <p>{@code cumulativeSavings} is the total net savings position at the end of month N,
 * compounding monthly net-savings. The frontend draws "Before" and "After" lines from
 * arrays of these points.
 */
@Data
@Builder
public class ProjectionPoint {
    private int        month;              // 1..N
    private BigDecimal cumulativeSavings;
    private BigDecimal monthlyNet;         // income - spend - recurring - scenarioCost
}
