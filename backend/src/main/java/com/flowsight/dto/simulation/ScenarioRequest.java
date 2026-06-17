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

/**
 * One hypothetical financial decision the user wants to model.
 *
 * <p>Field usage by type:
 * <ul>
 *   <li>ONE_TIME_PURCHASE: {@code amount}, optional {@code category}</li>
 *   <li>RECURRING_EXPENSE: {@code amount} (monthly), optional {@code category}, optional {@code durationMonths}</li>
 *   <li>SAVINGS_ADJUSTMENT: signed {@code amount} (+ = increase savings, − = reduce), optional {@code durationMonths}</li>
 *   <li>LOAN_EMI: {@code amount} (principal), {@code annualInterestRate}, {@code tenureMonths}</li>
 * </ul>
 */
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
