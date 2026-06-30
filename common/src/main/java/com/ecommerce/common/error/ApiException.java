package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for application exceptions that map to a specific HTTP status.
 * {@link GlobalExceptionHandler} renders any ApiException as an RFC-7807 ProblemDetail.
 * Subclasses fix the status; callers supply a human-readable, client-safe message.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** Preserves the originating cause (e.g. a circuit-breaker failure) for logging/triage. */
    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
