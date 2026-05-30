package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The user's current financial state, computed from their actual transaction
 * history. All inputs to the simulation engines come from this baseline.
 */
@Data
@Builder
public class FinancialBaseline {
    private BigDecimal monthlyIncome;
    private BigDecimal monthlySpend;
    private BigDecimal monthlyRecurring;
    private BigDecimal monthlyDiscretionary;
    private BigDecimal monthlyNetSavings;
    private double     savingsRate;             // 0–1
    /** Months of transaction data we averaged across (max 3, can be 1–3). */
    private int        dataMonths;
    /** Top category by spend over the analysis window. */
    private String     topCategoryName;
    private BigDecimal topCategoryMonthlySpend;
    private boolean    hasEnoughData;
}
