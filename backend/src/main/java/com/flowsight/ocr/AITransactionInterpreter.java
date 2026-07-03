package com.flowsight.ocr;

import java.util.List;
import java.util.Optional;

// AI fallback for ambiguous/low-confidence merchant extraction; returns empty on failure.
// Scope: merchant name, category hint, confidence only (never amounts/dates).
public interface AITransactionInterpreter {

    // rawOcrText: truncated OCR lines; merchantCandidates: up to 3 ranked hints; empty if AI unavailable
    Optional<AIInterpretation> interpret(String rawOcrText, List<String> merchantCandidates);
}
