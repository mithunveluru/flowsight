package com.flowsight.dto.transaction;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateTransactionRequest {

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Invalid amount format")
    private BigDecimal amount;

    @Size(max = 3)
    private String currency;

    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;

    @Size(max = 1000)
    private String description;

    @Size(max = 255)
    private String merchant;

    private TransactionType type;

    private TransactionCategory category;

    @Size(max = 2000)
    private String notes;

    private Boolean reviewed;
}
