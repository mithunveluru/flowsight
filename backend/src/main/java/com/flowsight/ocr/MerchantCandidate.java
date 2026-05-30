package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

/**
 * Heuristic extraction result from {@link MerchantExtractor#extractWithScore}.
 *
 * {@code score} reflects how confidently the heuristic engine identified this
 * line as a merchant name (position + OCR confidence + capitalisation + content).
 *
 * {@code ambiguous} is true when a competing candidate scored within
 * {@link MerchantExtractor#AMBIGUITY_MARGIN} of the winner — a signal that
 * the AI fallback should adjudicate.
 */
@Value
@Builder
public class MerchantCandidate {

    String  name;
    double  score;
    boolean ambiguous;

    /** True when the heuristic score is below the reliable-extraction threshold. */
    public boolean isLowConfidence() {
        return score < MerchantExtractor.LOW_CONFIDENCE_THRESHOLD;
    }

    /** True when the AI fallback should be invoked to resolve uncertainty. */
    public boolean needsAI() {
        return isLowConfidence() || ambiguous;
    }
}
