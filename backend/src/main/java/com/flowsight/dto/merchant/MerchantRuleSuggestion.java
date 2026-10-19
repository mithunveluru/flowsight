package com.flowsight.dto.merchant;

import com.flowsight.entity.TransactionCategory;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

// Returned after a correction: the rule it saved and how many other transactions it would change.
@Data
@Builder
public class MerchantRuleSuggestion {
    private UUID ruleId;
    private String canonicalName;
    private TransactionCategory category;
    private int similarCount;
}
