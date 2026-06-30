package com.ecommerce.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a requested entity does not exist. Renders as HTTP 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /** Convenience: "{resource} not found: {id}". */
    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, resource + " not found: " + id);
    }
}
