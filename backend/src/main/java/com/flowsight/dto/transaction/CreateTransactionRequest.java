package com.flowsight.dto.transaction;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @Size(max = 3, message = "Currency must be a 3-letter code")
    private String currency = "INR";

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 255, message = "Merchant name must not exceed 255 characters")
    private String merchant;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    // Optional: if provided, overrides the auto-categorization result
    private TransactionCategory category;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
