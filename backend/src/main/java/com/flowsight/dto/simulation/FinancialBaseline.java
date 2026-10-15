package com.flowsight.dto.simulation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// The user's current financial state from transaction history; input to the simulation engines.
@Data
@Builder
public class FinancialBaseline {
    private BigDecimal monthlyIncome;
    private BigDecimal monthlySpend;
    private BigDecimal monthlyRecurring;
    private BigDecimal monthlyDiscretionary;
    private BigDecimal monthlyNetSavings;
    private double     savingsRate;             // 0–1
    // Complete calendar months of history averaged across (0-3; 0 means no baseline yet).
    private int        dataMonths;
    // Top category by spend over the analysis window.
    private String     topCategoryName;
    private BigDecimal topCategoryMonthlySpend;
    private boolean    hasEnoughData;
}
