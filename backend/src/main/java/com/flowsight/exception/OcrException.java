package com.flowsight.exception;

import org.springframework.http.HttpStatus;

public class OcrException extends FlowsightException {

    public OcrException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public OcrException(String message, Throwable cause) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, cause);
    }
}
