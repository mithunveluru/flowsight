package com.flowsight.ml;

// Where the correction was made. Add constants freely: stored as VARCHAR(30), no CHECK.
public enum FeedbackSource {
    RECEIPT_REVIEW,
    TRANSACTION_EDIT,
    RECURRING_REVIEW
}
