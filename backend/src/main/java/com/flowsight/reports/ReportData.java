package com.flowsight.reports;

import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.insights.BehavioralProfile;
import com.flowsight.dto.insights.ConsequenceProjection;
import com.flowsight.dto.insights.Recommendation;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Aggregation model: everything the PDF needs from analytics. Insights-only, no raw transactions.
@Data
@Builder
public class ReportData {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String    periodLabel;          // "1 May 2026 – 30 May 2026"

    // totals, categories, merchants, alerts
    private AnalyticsOverviewResponse currentPeriod;
    private AnalyticsOverviewResponse priorPeriod;        // same-length prior window, for deltas

    // active recurring patterns
    private List<RecurringPatternResponse> recurringPatterns;
    private BigDecimal                    monthlyRecurringTotal;
    private BigDecimal                    annualRecurringTotal;

    private LeakDetectionResponse leaks;

    // behaviour + recommendations + consequences
    private BehavioralProfile         behavioralProfile;
    private List<Recommendation>      recommendations;
    private List<ConsequenceProjection> topConsequences;

    // spend delta vs prior window
    private BigDecimal spendChange;
    private double    spendChangePercent;
}
