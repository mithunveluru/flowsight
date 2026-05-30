package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsequenceInsight {
    private String code;            // CASH_FLOW | RECURRING_LOAD | CATEGORY_SHIFT | GOAL_DELAY | OPPORTUNITY_COST | SAVINGS_BOOST
    private String severity;        // POSITIVE | NEUTRAL | CAUTION | WARNING
    private String title;
    private String description;
}
