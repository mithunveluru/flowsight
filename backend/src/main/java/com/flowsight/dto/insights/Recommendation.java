package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// An actionable suggestion tied to leaks, recurring patterns, or behaviors.
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
    // Supporting facts shown as small footnotes on the card.
    private List<String> evidence;

    // Stable per user and subject; used to accept or dismiss. The same advice keeps its id across requests.
    private String id;
    private String kind;                        // e.g. LEAK_BANK_FEES, BEHAVIOR_WEEKEND_OVERSPEND, GOAL_REDIRECT
    private String source;                      // LEAK | BEHAVIOR | GOAL
    private Double acceptanceRate;              // posterior chance the user acts on this kind of advice
    private Double rankScore;                   // acceptanceRate * (1 + ln(1 + saving / 100))
    private String rankingReason;
    private String feedback;                    // ACCEPTED when the user accepted it in the last 30 days
}
