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
    // Sum of the leak-based savings; behavior estimates overlap with leaks and each other, so they are not added.
    private BigDecimal totalPotentialMonthlySaving;
    private BigDecimal totalPotentialAnnualSaving;
    // a short grounded summary; source LLM or TEMPLATE
    private InsightNarrative narrative;
}
