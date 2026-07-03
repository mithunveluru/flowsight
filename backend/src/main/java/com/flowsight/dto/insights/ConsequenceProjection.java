package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// Long-term cost of a recurring expense; tenYearOpportunityCost = FV of the same amount invested monthly.
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
    // Short reflection prompt — used as the card subtitle.
    private String reflection;
}
