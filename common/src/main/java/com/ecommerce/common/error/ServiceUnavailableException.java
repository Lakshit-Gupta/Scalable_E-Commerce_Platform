package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a dependency is temporarily unavailable (e.g. an open circuit breaker). Renders as HTTP 503. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
