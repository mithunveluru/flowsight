package com.flowsight.dto.budget;

import com.flowsight.ml.forecast.ForecastEvidence;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BudgetResponse {
    private UUID       id;
    // null = overall budget
    private String     category;
    private String     categoryDisplayName;
    private BigDecimal monthlyLimit;
    private boolean    rollover;
    // limit carried from earlier months (0 without rollover) and the limit this month after it
    private BigDecimal rolloverAmount;
    private BigDecimal effectiveLimit;
    private boolean    isActive;
    // Live tracking against current calendar month
    private BigDecimal currentSpend;
    private BigDecimal remaining;
    private double     percentUsed;
    private BigDecimal projectedTotal;
    private int        daysRemaining;
    // ON_TRACK | NEAR_LIMIT | OVER | PROJECTED_OVER
    private String     status;
    // how projectedTotal was made, with its prediction interval
    private Pace       pace;

    @Data
    @Builder
    public static class Pace {
        private String           method;          // forecast-remaining | month-to-date-pace | none
        private double           remainingShare;  // share of a typical month's spend still ahead
        private BigDecimal       lower80;
        private BigDecimal       upper80;
        private BigDecimal       lower95;
        private BigDecimal       upper95;
        private ForecastEvidence evidence;
    }
}
