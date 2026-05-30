package com.flowsight.dto.receipt;

import com.flowsight.entity.TransactionCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * User-reviewed and potentially corrected values submitted to confirm a receipt draft.
 *
 * The user has reviewed (and may have edited) the OCR-extracted data on the review
 * screen before POSTing here. This is the source of truth for transaction creation
 * — not the raw OCR output.
 */
@Data
@NoArgsConstructor
public class ReceiptConfirmRequest {

    @NotBlank(message = "Merchant name is required")
    @Size(max = 255)
    private String merchant;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate date;

    /** null = auto-detect via CategorizationService */
    private TransactionCategory category;

    @Size(max = 500)
    private String notes;

    @Size(max = 3)
    private String currency;
}
