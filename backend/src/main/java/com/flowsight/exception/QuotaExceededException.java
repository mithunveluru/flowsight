package com.flowsight.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts an action gated by a usage quota and the quota
 * is exhausted. Maps to HTTP 402 Payment Required, signalling "the action is
 * legitimate but you've used your share." The handler keeps the message
 * user-friendly so the frontend can surface it directly.
 */
public class QuotaExceededException extends FlowsightException {
    public QuotaExceededException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED);
    }
}
