package com.flowsight.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> violations = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.builder()
                .status(400)
                .error("Validation Failed")
                .message("Request validation failed")
                .violations(violations)
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(FlowsightException.class)
    public ResponseEntity<ApiError> handleFlowsight(FlowsightException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(ApiError.builder()
                .status(ex.getStatusCode())
                .error(HttpStatus.valueOf(ex.getStatusCode()).getReasonPhrase())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build());
    }

    // Intentionally generic message — do not reveal whether email exists or not
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.builder()
                .status(401)
                .error("Unauthorized")
                .message("Invalid email or password")
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.builder()
                .status(401)
                .error("Unauthorized")
                .message("Account is disabled. Please contact support.")
                .timestamp(Instant.now())
                .build());
    }

    // Handles race-condition duplicate email that bypasses the existsByEmail pre-check
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("users_email_key")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.builder()
                    .status(409)
                    .error("Conflict")
                    .message("An account with this email already exists")
                    .timestamp(Instant.now())
                    .build());
        }
        log.error("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.builder()
                .status(403)
                .error("Forbidden")
                .message("Access denied")
                .timestamp(Instant.now())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .build());
    }
}
