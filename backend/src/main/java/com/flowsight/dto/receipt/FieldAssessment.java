package com.flowsight.dto.receipt;

// Per-field result of extraction: calibrated probability the value is right, the review
// decision, and a short machine-readable basis such as "arithmetic+label".
public record FieldAssessment(Double confidence, ReceiptDecision decision, String basis) {

    public static FieldAssessment of(double confidence, String basis) {
        double c = Math.max(0.0, Math.min(1.0, confidence));
        return new FieldAssessment(Math.round(c * 1000) / 1000.0, ReceiptDecision.of(c), basis);
    }

    public static FieldAssessment ask(double confidence, String basis) {
        double c = Math.max(0.0, Math.min(1.0, confidence));
        return new FieldAssessment(Math.round(c * 1000) / 1000.0, ReceiptDecision.ASK_USER, basis);
    }

    public static FieldAssessment missing() {
        return new FieldAssessment(0.0, ReceiptDecision.ASK_USER, "not found");
    }
}
