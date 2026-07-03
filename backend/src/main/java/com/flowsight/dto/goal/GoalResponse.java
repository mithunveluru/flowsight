package com.flowsight.dto.goal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class GoalResponse {
    private UUID       id;
    private String     name;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal remaining;
    private LocalDate  targetDate;
    private String     icon;
    private String     status;
    // Computed live: percent complete (0–100)
    private double     percentComplete;
    private int        daysRemaining;
    // Daily contribution needed to hit the goal on time
    private BigDecimal dailyPaceRequired;
    // ON_PACE | AHEAD | BEHIND | COMPLETED | OVERDUE
    private String     paceStatus;
}
