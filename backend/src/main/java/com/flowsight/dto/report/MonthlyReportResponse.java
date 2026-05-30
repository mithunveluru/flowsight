package com.flowsight.dto.report;

import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.analytics.MerchantSummary;
import com.flowsight.dto.analytics.SpendAlert;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MonthlyReportResponse {
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String    periodLabel;     // "May 2026"
    private BigDecimal totalSpend;
    private BigDecimal totalIncome;
    private BigDecimal netCashflow;
    private int        transactionCount;
    private List<CategoryBreakdownItem> categoryBreakdown;
    private List<MerchantSummary>       topMerchants;
    private List<SpendAlert>            alerts;
    private TaxSummaryResponse          taxSummary;
}
