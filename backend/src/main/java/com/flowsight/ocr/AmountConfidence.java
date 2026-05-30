package com.flowsight.ocr;

/**
 * Confidence tier for an extracted receipt total amount.
 * The numeric value is used as the {@code OcrExtractionResult.confidence} score.
 */
public enum AmountConfidence {
    HIGH(0.85),
    MEDIUM(0.60),
    LOW(0.30);

    private final double numericValue;

    AmountConfidence(double numericValue) {
        this.numericValue = numericValue;
    }

    public double getNumericValue() {
        return numericValue;
    }
}
