package com.flowsight.dto.insights;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class InsightsResponse {
    private BehavioralProfile          profile;
    private List<Recommendation>       recommendations;
    private List<ConsequenceProjection> topConsequences;
    /** Sum of all monthly savings if every recommendation is acted on. */
    private BigDecimal totalPotentialMonthlySaving;
    private BigDecimal totalPotentialAnnualSaving;
}
