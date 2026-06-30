package com.ecommerce.common.error;

import com.ecommerce.common.web.CorrelationConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centralised RFC-7807 error rendering for every servlet service.
 *
 * <p>Auto-registered via {@code com.ecommerce.common.config.CommonAutoConfiguration}; services get
 * consistent {@link ProblemDetail} responses just by depending on the common library. Extends
 * {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions (404, 405, unreadable body…)
 * are already returned as ProblemDetail; we add domain exceptions, Bean Validation details, and a
 * safe catch-all.
 *
 * <p>Every response carries {@code timestamp}, {@code correlationId} (from the MDC), and {@code instance}
 * (the request path) as extension members.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://errors.ecommerce.com/";

    /** Bean Validation on @RequestBody (@Valid) — collect per-field messages. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid value" : fe.getDefaultMessage()));
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        body.setTitle("Validation failed");
        body.setType(URI.create(TYPE_BASE + "validation"));
        body.setProperty("errors", errors);
        enrich(body, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(body);
    }

    /** Bean Validation on @Validated path/query params (@NotBlank on a @RequestParam, etc.). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            errors.add(Map.of(
                "field", v.getPropertyPath().toString(),
                "message", v.getMessage()));
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        body.setTitle("Validation failed");
        body.setType(URI.create(TYPE_BASE + "validation"));
        body.setProperty("errors", errors);
        enrich(body, request);
        return body;
    }

    /** Any domain exception that declares its own HTTP status. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        body.setType(URI.create(TYPE_BASE + ex.getStatus().value()));
        enrich(body, request);
        return body;
    }

    /** Safety net: never leak internals; log with the correlation id for triage. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        body.setTitle("Internal Server Error");
        body.setType(URI.create(TYPE_BASE + "500"));
        enrich(body, request);
        return body;
    }

    private void enrich(ProblemDetail body, WebRequest request) {
        body.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get(CorrelationConstants.MDC_KEY);
        if (correlationId != null) {
            body.setProperty("correlationId", correlationId);
        }
        // request.getDescription(false) -> "uri=/path"
        String desc = request.getDescription(false);
        if (desc != null && desc.startsWith("uri=")) {
            body.setInstance(URI.create(desc.substring(4)));
        }
    }
}
