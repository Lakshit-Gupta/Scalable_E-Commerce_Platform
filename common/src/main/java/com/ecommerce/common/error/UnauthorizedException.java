package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when authentication fails or credentials are invalid. Renders as HTTP 401. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
