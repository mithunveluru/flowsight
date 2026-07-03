package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// One detected behavioral spending pattern, derived only from transaction history.
@Data
@Builder
public class BehavioralPattern {
    private String     code;            // WEEKEND_OVERSPEND | LIFESTYLE_INFLATION | CATEGORY_CONCENTRATION | INCREASING_FREQUENCY | LARGE_TICKET_TREND
    private String     title;
    private String     description;
    private String     severity;        // HIGH | MEDIUM | LOW
    private BigDecimal value;           // dimension-specific: ratio, percent, amount
    private String     unit;            // "%", "x", "₹"
    private String     context;         // a short supporting sentence
}
