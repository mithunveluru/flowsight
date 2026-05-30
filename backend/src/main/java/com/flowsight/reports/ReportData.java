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

/**
 * Intermediate aggregation model — bundles every data point the PDF needs from
 * the existing analytics services. The analytics aggregator populates this; the
 * insight generator reads from it; the PDF builder renders from it.
 *
 * <p>No raw transactions go in this model. The report is insights-only, never
 * a spreadsheet export.
 */
@Data
@Builder
public class ReportData {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String    periodLabel;          // "1 May 2026 – 30 May 2026"

    // Phase 5 — totals + category breakdown + top merchants + alerts
    private AnalyticsOverviewResponse currentPeriod;
    private AnalyticsOverviewResponse priorPeriod;        // same-length window ending one period before — for deltas

    // Phase 6 — active recurring patterns
    private List<RecurringPatternResponse> recurringPatterns;
    private BigDecimal                    monthlyRecurringTotal;
    private BigDecimal                    annualRecurringTotal;

    // Phase 7 — leaks
    private LeakDetectionResponse leaks;

    // Phase 10 — behavioural patterns + recommendations + consequence projections
    private BehavioralProfile         behavioralProfile;
    private List<Recommendation>      recommendations;
    private List<ConsequenceProjection> topConsequences;

    // Period-over-period spend delta (current vs prior window)
    private BigDecimal spendChange;
    private double    spendChangePercent;
}
