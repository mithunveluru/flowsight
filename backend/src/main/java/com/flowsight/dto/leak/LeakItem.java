package com.flowsight.dto.leak;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

// One merchant/transaction underlying a LeakInsight (rendered as a table row).
@Data
@Builder
public class LeakItem {
    private String     merchant;
    private BigDecimal amount;
    private String     detail;       // e.g. "12x this month" or "Avg ₹150/visit"
    private String     category;     // enum name for color coding
    private String     categoryLabel;
}
