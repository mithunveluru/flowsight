package com.flowsight.dto.transaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowsight.dto.merchant.MerchantRuleSuggestion;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private String description;
    private String merchant;
    private TransactionCategory category;
    private String categoryDisplayName;
    private TransactionType type;
    private TransactionSource source;
    private BigDecimal confidenceScore;
    private String notes;
    private boolean reviewed;
    private Instant createdAt;
    // only on a merchant/category correction that saved a rule
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MerchantRuleSuggestion ruleSuggestion;
}
