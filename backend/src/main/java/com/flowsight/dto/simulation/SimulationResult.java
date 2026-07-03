package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SimulationResult {
    private FinancialBaseline baseline;
    private ScenarioRequest   scenario;

    // Net change to monthly cash flow (negative = costs more, positive = saves more).
    private BigDecimal monthlyImpact;
    private BigDecimal yearlyImpact;
    private BigDecimal fiveYearImpact;
    private BigDecimal tenYearCost;              // simple linear cost over 10 years
    private BigDecimal tenYearOpportunityCost;   // future value if invested at 8%

    private FlexibilityScore flexibility;
    private Projection       projection;
    private List<Tradeoff>   tradeoffs;
    private List<ConsequenceInsight> insights;
    private GoalImpact       goalImpact;     // null when user has no active goals
}
