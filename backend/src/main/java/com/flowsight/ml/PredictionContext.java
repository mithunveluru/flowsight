package com.flowsight.ml;

import java.util.UUID;

// Who and what a prediction is for; recorded in prediction_log. All fields optional.
public record PredictionContext(UUID userId, SubjectType subjectType, UUID subjectId) {

    public static final PredictionContext NONE = new PredictionContext(null, null, null);

    public static PredictionContext forUser(UUID userId) {
        return new PredictionContext(userId, null, null);
    }

    public static PredictionContext of(UUID userId, SubjectType subjectType, UUID subjectId) {
        return new PredictionContext(userId, subjectType, subjectId);
    }
}
