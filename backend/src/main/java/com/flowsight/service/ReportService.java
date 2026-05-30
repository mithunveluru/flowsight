package com.flowsight.service;

import com.flowsight.analytics.AnalyticsService;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.report.MonthlyReportResponse;
import com.flowsight.dto.report.TaxSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Composes monthly summary reports (HTML-printable) for the user.
 * Reuses Phase 5 analytics and the Phase 9 tax-deduction detector — no new persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final AnalyticsService     analyticsService;
    private final TaxDeductionDetector taxDeductionDetector;

    /**
     * Composes the monthly report for the given date range.
     * Tax summary is always for the *current* financial year (orthogonal to the range
     * since FY accumulates across the year).
     */
    public MonthlyReportResponse buildMonthlyReport(UUID userId, LocalDate from, LocalDate to) {
        AnalyticsOverviewResponse overview = analyticsService.getOverview(userId, from, to);
        TaxSummaryResponse        taxSummary = taxDeductionDetector.detectForFinancialYear(userId, LocalDate.now());

        String label = from.withDayOfMonth(1).equals(to.withDayOfMonth(1))
            ? from.format(MONTH_LABEL)
            : from.format(MONTH_LABEL) + " – " + to.format(MONTH_LABEL);

        return MonthlyReportResponse.builder()
            .periodStart(from)
            .periodEnd(to)
            .periodLabel(label)
            .totalSpend(overview.getTotalSpend())
            .totalIncome(overview.getTotalIncome())
            .netCashflow(overview.getNetCashflow())
            .transactionCount(overview.getTransactionCount())
            .categoryBreakdown(overview.getCategoryBreakdown())
            .topMerchants(overview.getTopMerchants())
            .alerts(overview.getAlerts())
            .taxSummary(taxSummary)
            .build();
    }
}
