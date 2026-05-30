package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AnalyticsOverviewResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal totalSpend;
    private BigDecimal totalIncome;
    private BigDecimal netCashflow;
    private int transactionCount;
    private List<CategoryBreakdownItem> categoryBreakdown;
    private List<MerchantSummary> topMerchants;
    private List<SpendAlert> alerts;
}
