package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * A scored candidate for the receipt's final payable amount.
 * Multiple candidates are built (one from receipt-ocr's total_amount and one per
 * financial-summary line item), then the highest-scored one is selected.
 */
@Value
@Builder
public class AmountCandidate {
    BigDecimal      amount;
    String          label;       // source label — null means it came from total_amount directly
    double          score;       // 0.0–1.0
    AmountConfidence confidence;
    String          reason;      // debug/log explanation
}
