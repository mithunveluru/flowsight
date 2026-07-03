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

// User-reviewed OCR values submitted to confirm a receipt draft; source of truth for the transaction.
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

    // null = auto-detect via CategorizationService
    private TransactionCategory category;

    @Size(max = 500)
    private String notes;

    @Size(max = 3)
    private String currency;
}
