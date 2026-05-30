package com.flowsight.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TaxSummaryResponse {
    private int        financialYear;   // start year (FY 2025-26 → 2025)
    private LocalDate  periodStart;
    private LocalDate  periodEnd;
    private BigDecimal totalEligible;
    private List<TaxSection> sections;
}
