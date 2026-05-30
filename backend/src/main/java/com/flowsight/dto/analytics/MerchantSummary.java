package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MerchantSummary {
    private String merchant;
    private BigDecimal totalAmount;
    private int transactionCount;
}
