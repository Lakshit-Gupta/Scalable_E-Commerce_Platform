package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a request conflicts with current state (e.g. duplicate). Renders as HTTP 409. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
