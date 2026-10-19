package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

// Heuristic merchant result: score + ambiguous flag (competing candidate within AMBIGUITY_MARGIN).
// rawText is the receipt line as read; name is the cleaned form of it.
@Value
@Builder
public class MerchantCandidate {

    String  name;
    String  rawText;
    double  score;
    boolean ambiguous;

    // below the reliable-extraction threshold
    public boolean isLowConfidence() {
        return score < MerchantExtractor.LOW_CONFIDENCE_THRESHOLD;
    }

    // invoke AI when uncertain
    public boolean needsAI() {
        return isLowConfidence() || ambiguous;
    }
}
