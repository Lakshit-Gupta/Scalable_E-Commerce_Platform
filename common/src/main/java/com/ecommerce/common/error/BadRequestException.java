package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/** Thrown for semantically invalid requests not caught by Bean Validation. Renders as HTTP 400. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
