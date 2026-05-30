package com.flowsight.exception;

import org.springframework.http.HttpStatus;

public class FlowsightException extends RuntimeException {

    private final int statusCode;

    public FlowsightException(String message, HttpStatus status) {
        super(message);
        this.statusCode = status.value();
    }

    public FlowsightException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.statusCode = status.value();
    }

    public FlowsightException(String message) {
        super(message);
        this.statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    public int getStatusCode() {
        return statusCode;
    }
}
