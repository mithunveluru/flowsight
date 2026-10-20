package com.flowsight.ml;

// modelVersion and confidence are null when the rule-based result was served. outcome says why:
// null when ml-service was never asked (unconfigured, flag off, shadow), else the call's result.
public record MlPrediction<T>(T value, Source source, String modelVersion, Double confidence, MlOutcome outcome) {

    public enum Source { MODEL, RULES }

    public MlPrediction(T value, Source source, String modelVersion, Double confidence) {
        this(value, source, modelVersion, confidence, source == Source.MODEL ? MlOutcome.OK : null);
    }

    static <T> MlPrediction<T> rules(T value) {
        return new MlPrediction<>(value, Source.RULES, null, null, null);
    }

    static <T> MlPrediction<T> rules(T value, MlOutcome outcome) {
        return new MlPrediction<>(value, Source.RULES, null, null, outcome);
    }

    static <T> MlPrediction<T> model(T value, String modelVersion, Double confidence) {
        return new MlPrediction<>(value, Source.MODEL, modelVersion, confidence, MlOutcome.OK);
    }

    public boolean fromModel() {
        return source == Source.MODEL;
    }
}
