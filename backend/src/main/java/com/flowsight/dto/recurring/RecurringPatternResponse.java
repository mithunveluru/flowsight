package com.flowsight.dto.recurring;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class RecurringPatternResponse {
    private UUID      id;
    private String    merchant;
    private String    period;           // enum name
    private String    periodLabel;      // "Monthly", "Every 2 weeks", etc.
    private BigDecimal estimatedAmount;
    private BigDecimal annualCost;
    private BigDecimal monthlyEquivalent;
    private LocalDate lastSeenDate;
    private LocalDate nextExpectedDate;
    private int       occurrenceCount;
    private BigDecimal confidence;
    // Tier for UI badge: HIGH | MEDIUM | POSSIBLE
    private String    confidenceTier;
    private boolean   isCancellationCandidate;
    private boolean   isDismissed;
    private boolean   isUserConfirmed;
    // Status for frontend badge: ACTIVE | DUE_SOON | OVERDUE | MISSED
    private String    status;
    private int       daysUntilNext;    // negative when overdue
}
