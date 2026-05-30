package com.flowsight.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CategoryBreakdownItem {
    private String category;           // enum name, used by frontend for coloring
    private String displayName;
    private BigDecimal amount;
    private double percentage;         // share of total spend (0–100)
    private int transactionCount;
}
