package com.flowsight.ml;

import java.util.UUID;

/**
 * One user correction of a predicted value. predictedValue is null when nothing was
 * predicted; modelVersion is null when the rule engine made the prediction.
 */
public record FeedbackCorrection(
    SubjectType subjectType,
    UUID subjectId,
    String field,
    String predictedValue,
    String correctedValue,
    FeedbackSource source,
    String modelVersion,
    Double confidence
) {
}
