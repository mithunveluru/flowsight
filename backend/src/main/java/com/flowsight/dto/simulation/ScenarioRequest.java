package com.flowsight.dto.simulation;

import com.flowsight.entity.TransactionCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// One hypothetical decision to model; which fields apply depends on the scenario type.
@Data
@NoArgsConstructor
public class ScenarioRequest {

    @NotNull
    private ScenarioType type;

    private String name;

    // Signed for SAVINGS_ADJUSTMENT; magnitude capped to keep projections bounded.
    @NotNull
    @DecimalMin(value = "-1000000000.00")
    @DecimalMax(value = "1000000000.00")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    private TransactionCategory category;

    // Optional horizon (months). When supplied, bounded to a sane planning window.
    @Min(1)
    @Max(600)
    private Integer durationMonths;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private BigDecimal annualInterestRate;

    @Min(1)
    @Max(600)
    private Integer tenureMonths;
}
