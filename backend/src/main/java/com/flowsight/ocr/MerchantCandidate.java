package com.flowsight.ocr;

import lombok.Builder;
import lombok.Value;

// Heuristic merchant result: score + ambiguous flag (competing candidate within AMBIGUITY_MARGIN).
@Value
@Builder
public class MerchantCandidate {

    String  name;
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
