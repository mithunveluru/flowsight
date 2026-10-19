package com.flowsight.ml.categorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** The result object of ml-service's categorizer: one prediction per request item, in order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategorizerResponse(List<Prediction> predictions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(String category, double probability, List<Alternative> alternatives) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alternative(String category, double probability) {
    }
}
