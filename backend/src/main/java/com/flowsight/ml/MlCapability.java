package com.flowsight.ml;

// Capability ids shared with ml-service (POST /v1/capabilities/{id}/predict) and with the
// application.ml.capabilities.{id} flags.
public enum MlCapability {
    CATEGORIZER("categorizer"),
    MERCHANT_EMBEDDINGS("merchant-embeddings"),
    RECURRING_SCORER("recurring-scorer"),
    FORECASTER("forecaster"),
    OCR("ocr"),
    NARRATIVE("narrative");

    private final String id;

    MlCapability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
