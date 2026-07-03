package com.flowsight.dto.budget;

import com.flowsight.entity.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class BudgetRequest {

    // null = overall budget
    private TransactionCategory category;

    @NotNull(message = "Monthly limit is required")
    @DecimalMin(value = "0.01", message = "Monthly limit must be greater than zero")
    private BigDecimal monthlyLimit;

    private boolean rollover = false;
}
