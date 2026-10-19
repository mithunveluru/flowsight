package com.flowsight.dto.merchant;

import com.flowsight.entity.TransactionCategory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MerchantRuleResponse {
    private UUID id;
    private String matchKey;
    private String canonicalName;
    private TransactionCategory category;
    private int appliedCount;
    private Instant createdAt;
    private Instant updatedAt;
}
