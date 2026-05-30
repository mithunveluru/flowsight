package com.flowsight.ocr;

import java.util.List;
import java.util.Optional;

/**
 * Fallback AI interpreter invoked only when heuristic extraction is ambiguous or
 * low-confidence. Implementations must be non-blocking-tolerant — they should
 * return {@code Optional.empty()} on any network failure rather than propagating
 * exceptions, so the heuristic result is always used as the safe fallback.
 *
 * Scope is intentionally narrow: merchant name, category hint, and confidence.
 * Deterministic fields (amount, date, taxes) are never passed to AI.
 */
public interface AITransactionInterpreter {

    /**
     * @param rawOcrText        first {@code N} lines of OCR text (caller-truncated to limit tokens)
     * @param merchantCandidates up to 3 heuristic merchant candidates, ranked best-first
     * @return structured interpretation, or empty if AI is unavailable or fails
     */
    Optional<AIInterpretation> interpret(String rawOcrText, List<String> merchantCandidates);
}
