package com.flowsight.dto.budget;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BudgetResponse {
    private UUID       id;
    /** null = overall budget */
    private String     category;
    private String     categoryDisplayName;
    private BigDecimal monthlyLimit;
    private boolean    rollover;
    private boolean    isActive;
    /** Live tracking against current calendar month */
    private BigDecimal currentSpend;
    private BigDecimal remaining;
    private double     percentUsed;
    private BigDecimal projectedTotal;
    private int        daysRemaining;
    /** ON_TRACK | NEAR_LIMIT | OVER | PROJECTED_OVER */
    private String     status;
}
