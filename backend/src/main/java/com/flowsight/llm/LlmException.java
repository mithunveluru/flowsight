package com.flowsight.llm;

public class LlmException extends Exception {

    public enum Reason { UNAVAILABLE, TIMEOUT, MODEL_UNAVAILABLE, RATE_LIMITED, HTTP_ERROR, MALFORMED, IO }

    private final Reason reason;

    public LlmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
