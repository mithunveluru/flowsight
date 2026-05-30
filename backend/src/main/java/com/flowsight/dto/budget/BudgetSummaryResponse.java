package com.flowsight.dto.budget;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BudgetSummaryResponse {
    private BigDecimal totalBudgeted;
    private BigDecimal totalSpent;
    private double     overallPercentUsed;
    private int        budgetCount;
    private int        overBudgetCount;
    private List<BudgetResponse> budgets;
}
