package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// One detected behavioral spending pattern, derived only from transaction history.
@Data
@Builder
public class BehavioralPattern {
    private String     code;            // WEEKEND_OVERSPEND | LIFESTYLE_INFLATION | CATEGORY_CONCENTRATION | INCREASING_FREQUENCY | LARGE_TICKET_TREND | CATEGORY_DRIFT
    private String     title;
    private String     description;
    private String     severity;        // HIGH | MEDIUM | LOW
    private BigDecimal value;           // dimension-specific: ratio, percent, amount
    private String     unit;            // "%", "x", "₹"
    private String     context;         // a short supporting sentence
    // the per-user level value had to reach to be flagged (same unit as value); null for fixed rules
    private BigDecimal threshold;
    // monthly amount that would bring this pattern back under the user's own threshold; null when not estimable
    private BigDecimal monthlySavingEstimate;
    private String     category;        // CATEGORY_DRIFT only
}
