package com.flowsight.dto.merchant;

import com.flowsight.entity.TransactionCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ApplyPreviewResponse {
    private UUID ruleId;
    private int matchCount;
    private List<Sample> sample;

    @Data
    @Builder
    public static class Sample {
        private UUID id;
        private LocalDate transactionDate;
        private String merchant;
        private TransactionCategory category;
        private BigDecimal amount;
        private String currency;
    }
}
