package com.flowsight.exception;

import org.springframework.http.HttpStatus;

// Usage quota exhausted; maps to HTTP 402. Message is user-facing.
public class QuotaExceededException extends FlowsightException {
    public QuotaExceededException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED);
    }
}
