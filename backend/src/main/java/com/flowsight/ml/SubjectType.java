package com.flowsight.ml;

// What a prediction or a correction is about. Stored as VARCHAR(30), no CHECK constraint.
public enum SubjectType {
    TRANSACTION,
    RECEIPT,
    RECURRING_PATTERN
}
