package com.flowsight.analytics.simulation;

import com.flowsight.dto.simulation.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

// 12-month before/after projection of cumulative savings for a scenario.
@Service
public class ProjectionEngine {

    private static final int PROJECTION_MONTHS = 12;

    public Projection project(FinancialBaseline baseline, ScenarioRequest scenario, BigDecimal monthlyImpact) {
        BigDecimal baselineMonthly = baseline.getMonthlyNetSavings();

        List<ProjectionPoint> before = buildLinearLine(baselineMonthly, BigDecimal.ZERO);
        List<ProjectionPoint> after  = buildScenarioLine(baselineMonthly, scenario, monthlyImpact);

        return Projection.builder().before(before).after(after).build();
    }

    // baseline: straight line at the monthly pace
    private List<ProjectionPoint> buildLinearLine(BigDecimal monthly, BigDecimal startingBalance) {
        List<ProjectionPoint> points = new ArrayList<>();
        BigDecimal running = startingBalance;
        for (int m = 1; m <= PROJECTION_MONTHS; m++) {
            running = running.add(monthly);
            points.add(ProjectionPoint.builder()
                .month(m)
                .cumulativeSavings(running.setScale(2, RoundingMode.HALF_UP))
                .monthlyNet(monthly.setScale(2, RoundingMode.HALF_UP))
                .build());
        }
        return points;
    }

    // scenario-adjusted line; shape depends on scenario type
    private List<ProjectionPoint> buildScenarioLine(
        BigDecimal baselineMonthly, ScenarioRequest scenario, BigDecimal monthlyImpactSign
    ) {
        List<ProjectionPoint> points = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;

        for (int m = 1; m <= PROJECTION_MONTHS; m++) {
            BigDecimal effectiveMonthly = baselineMonthly;
            BigDecimal oneTime          = BigDecimal.ZERO;

            switch (scenario.getType()) {
                case ONE_TIME_PURCHASE -> {
                    if (m == 1) oneTime = scenario.getAmount();
                }
                case RECURRING_EXPENSE -> {
                    int duration = scenario.getDurationMonths() != null ? scenario.getDurationMonths() : PROJECTION_MONTHS;
                    if (m <= duration) {
                        effectiveMonthly = effectiveMonthly.subtract(scenario.getAmount());
                    }
                }
                case SAVINGS_ADJUSTMENT -> {
                    int duration = scenario.getDurationMonths() != null ? scenario.getDurationMonths() : PROJECTION_MONTHS;
                    if (m <= duration) {
                        // positive amount increases savings
                        effectiveMonthly = effectiveMonthly.add(scenario.getAmount());
                    }
                }
                case LOAN_EMI -> {
                    int tenure = scenario.getTenureMonths() != null ? scenario.getTenureMonths() : PROJECTION_MONTHS;
                    BigDecimal emi = computeEmi(scenario);
                    if (m <= tenure) {
                        effectiveMonthly = effectiveMonthly.subtract(emi);
                    }
                }
            }

            running = running.add(effectiveMonthly).subtract(oneTime);
            points.add(ProjectionPoint.builder()
                .month(m)
                .cumulativeSavings(running.setScale(2, RoundingMode.HALF_UP))
                .monthlyNet(effectiveMonthly.subtract(oneTime).setScale(2, RoundingMode.HALF_UP))
                .build());
        }
        return points;
    }

    // EMI = P × r × (1+r)^n / ((1+r)^n − 1); 0 for ill-defined inputs
    public BigDecimal computeEmi(ScenarioRequest scenario) {
        if (scenario.getAmount() == null
         || scenario.getAnnualInterestRate() == null
         || scenario.getTenureMonths() == null
         || scenario.getTenureMonths() <= 0) {
            return BigDecimal.ZERO;
        }
        double p = scenario.getAmount().doubleValue();
        double r = scenario.getAnnualInterestRate().doubleValue() / 100.0 / 12.0;
        int    n = scenario.getTenureMonths();

        if (r <= 0) {
            // interest-free loan: equal principal split
            return BigDecimal.valueOf(p / n).setScale(2, RoundingMode.HALF_UP);
        }
        double emi = p * r * Math.pow(1 + r, n) / (Math.pow(1 + r, n) - 1);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }
}
