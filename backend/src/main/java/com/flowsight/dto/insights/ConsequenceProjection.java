package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The hidden long-term cost of a recurring expense.
 *
 * <p>{@link #tenYearOpportunityCost} is the future value of the same amount invested
 * monthly at an assumed annual return (typically 8%), using the future-value-of-annuity formula:
 * <pre>
 *   FV = P × ((1+r)^n − 1) / r
 * </pre>
 * where P = monthly payment, r = monthly rate (annual/12), n = months (120 for 10 years).
 *
 * <p>This is FlowSight's "decision consequence" — what you're truly paying when you keep
 * a subscription forever.
 */
@Data
@Builder
public class ConsequenceProjection {
    private String     label;                       // "Netflix" or category name
    private String     category;                    // optional grouping
    private BigDecimal monthlyAmount;
    private BigDecimal yearCost;
    private BigDecimal fiveYearCost;
    private BigDecimal tenYearCost;
    private BigDecimal tenYearOpportunityCost;      // FV if invested instead
    /** Short reflection prompt — used as the card subtitle. */
    private String reflection;
}
