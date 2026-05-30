package com.flowsight.dto.simulation;

import com.flowsight.entity.TransactionCategory;
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

    @NotNull
    private BigDecimal amount;

    private TransactionCategory category;
    private Integer    durationMonths;
    private BigDecimal annualInterestRate;
    private Integer    tenureMonths;
}
