package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// A behavioral spending alert from the analytics engine.
@Data
@Builder
public class SpendAlert {
    private String type;                // CATEGORY_SPIKE
    private String severity;            // HIGH | MEDIUM | LOW
    private String category;            // enum name
    private String categoryDisplayName;
    private String message;
    private BigDecimal currentAmount;
    private BigDecimal averageAmount;
    private double changePercent;       // e.g. 45.0 means 45% above average
}
