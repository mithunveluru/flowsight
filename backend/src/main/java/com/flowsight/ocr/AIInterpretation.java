package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

// Structured AI result; categoryHint is a TransactionCategory name or null.
@Value
@Builder
public class AIInterpretation {
    String merchant;
    String categoryHint; // TransactionCategory name or null
    double confidence;   // 0.0–1.0
}
