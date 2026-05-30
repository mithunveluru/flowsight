package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

/**
 * Structured result from an {@link AITransactionInterpreter}.
 *
 * {@code categoryHint} is a best-effort category string matching
 * {@link com.flowsight.entity.TransactionCategory} enum names.
 * It may be {@code null} when the AI cannot determine the category from receipt text alone.
 */
@Value
@Builder
public class AIInterpretation {
    String merchant;
    String categoryHint; // nullable — TransactionCategory name or null
    double confidence;   // 0.0–1.0
}
