package com.flowsight.ml;

// modelVersion and confidence are null when the rule-based result was served.
public record MlPrediction<T>(T value, Source source, String modelVersion, Double confidence) {

    public enum Source { MODEL, RULES }

    static <T> MlPrediction<T> rules(T value) {
        return new MlPrediction<>(value, Source.RULES, null, null);
    }

    static <T> MlPrediction<T> model(T value, String modelVersion, Double confidence) {
        return new MlPrediction<>(value, Source.MODEL, modelVersion, confidence);
    }

    public boolean fromModel() {
        return source == Source.MODEL;
    }
}
