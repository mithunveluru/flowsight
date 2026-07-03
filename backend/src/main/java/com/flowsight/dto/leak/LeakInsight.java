package com.flowsight.dto.leak;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// One detected leak: a category of recoverable spending.
@Data
@Builder
public class LeakInsight {
    private String     type;                  // DUPLICATE_SUBSCRIPTIONS | SUBSCRIPTION_CREEP | HIGH_FREQUENCY_SMALL_SPEND | BANK_FEES
    private String     severity;              // HIGH | MEDIUM | LOW
    private String     title;
    private String     description;
    private String     recommendation;
    private BigDecimal monthlyImpact;
    private BigDecimal annualImpact;
    private int        affectedItemsCount;
    private List<LeakItem> items;
}
