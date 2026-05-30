package com.flowsight.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TaxDeductionEntry {
    private String     merchant;
    private String     description;
    private BigDecimal amount;
    private String     date;          // ISO yyyy-MM-dd
    private String     detectedBy;    // keyword that triggered the match
}
