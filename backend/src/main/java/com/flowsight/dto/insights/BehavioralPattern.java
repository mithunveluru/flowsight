package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One detected behavioral pattern in a user's spending.
 *
 * <p>Patterns are derived purely from transaction history (date, amount, category, merchant)
 * — no external personalization data. Each pattern is annotated with a severity
 * tier and the magnitude of the deviation from the user's own baseline.
 */
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
