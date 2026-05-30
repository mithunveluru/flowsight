package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * An actionable suggestion tied to detected leaks, recurring patterns, or behaviors.
 */
@Data
@Builder
public class Recommendation {
    private String     type;                    // REDUCE_CATEGORY | CANCEL_SUBSCRIPTION | SHIFT_HABIT | REDIRECT_SAVINGS | REVIEW_INFLATION
    private String     title;
    private String     description;
    private String     suggestedAction;         // one-sentence next step
    private BigDecimal potentialMonthlySaving;
    private BigDecimal potentialAnnualSaving;
    private String     confidence;              // HIGH | MEDIUM | LOW
    /** Supporting facts shown as small footnotes on the card. */
    private List<String> evidence;
}
