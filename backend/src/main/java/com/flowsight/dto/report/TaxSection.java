package com.flowsight.dto.report;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TaxSection {
    private String     code;           // "80C", "80D", "80E"
    private String     name;           // "Tax-Saving Investments"
    private String     description;
    private BigDecimal totalAmount;
    private BigDecimal limit;          // ₹150000 for 80C, etc.
    private BigDecimal remainingLimit;
    private List<TaxDeductionEntry> entries;
}
