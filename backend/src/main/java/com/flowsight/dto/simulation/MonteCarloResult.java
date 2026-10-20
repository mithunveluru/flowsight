package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Distribution of outcomes over simulated paths. fan / baselineFan are fan-chart rows of cumulative
 * savings from today (with and without the scenario), one per month.
 */
@Data
@Builder
public class MonteCarloResult {
    private List<FanPoint> fan;
    private List<FanPoint> baselineFan;
    private GoalOdds       goal;                  // null without an active goal
    private Percentiles    flexibility;           // flexibility score with the scenario
    private Percentiles    baselineFlexibility;   // and without it
    private Evidence       evidence;

    public record FanPoint(int month, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}

    public record Percentiles(double p10, double p50, double p90) {}

    // reach months are months from now; a null percentile means that share of paths does not get there in 10 years
    public record ReachMonths(Integer p10, Integer p50, Integer p90) {}

    @Data
    @Builder
    public static class GoalOdds {
        private String      goalName;
        private BigDecimal  remaining;
        private LocalDate   targetDate;
        private int         monthsToTarget;
        private double      probabilityByTargetDate;
        private double      baselineProbabilityByTargetDate;
        private double      probabilityWithinHorizon;
        private ReachMonths reachMonth;
        private ReachMonths baselineReachMonth;
        // share of paths that reached the goal by month 1..horizonMonths, with the scenario
        private List<Double> reachProbabilityByMonth;
    }

    /**
     * How the distribution was made. volatilitySource: OWN_HISTORY (12+ months, own swings dominate),
     * SHRUNK_TO_PRIOR (some history, blended with the global prior) or PRIOR (under 3 months: normal draws).
     */
    @Data
    @Builder
    public static class Evidence {
        private String     method;
        private int        paths;
        private long       seed;
        private int        horizonMonths;
        private int        blockLength;
        private int        residualMonths;
        private List<String> window;
        private BigDecimal baseMonthlyNet;
        private BigDecimal ownMonthlySd;          // null with under 2 months
        private BigDecimal priorMonthlySd;
        private BigDecimal monthlySd;             // what was simulated
        private double     ownWeight;
        private String     volatilitySource;
        private String     note;
    }
}
