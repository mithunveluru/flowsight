package com.flowsight.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends FlowsightException {

    public DuplicateEmailException(String email) {
        super("An account with email '" + email + "' already exists", HttpStatus.CONFLICT);
    }
}
