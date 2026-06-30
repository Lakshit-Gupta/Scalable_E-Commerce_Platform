package com.ecommerce.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders gateway-origin errors (no route -> 404, no service instance / circuit open -> 503,
 * unexpected -> 500) as RFC-7807 {@code application/problem+json}, mirroring the servlet services'
 * common GlobalExceptionHandler so error shape is uniform across the platform. Ordered before
 * Boot's DefaultErrorWebExceptionHandler (which is -1).
 */
@Component
@Order(-2)
public class ProblemDetailErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final String CID_HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(ProblemDetailErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        if (status.is5xxServerError()) {
            log.error("Gateway error on {}: {}", exchange.getRequest().getPath().value(), ex.toString(), ex);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(CID_HEADER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", ex.getMessage() == null ? status.getReasonPhrase() : ex.getMessage());
        body.put("instance", exchange.getRequest().getPath().value());
        body.put("timestamp", Instant.now().toString());
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"status\":" + status.value() + "}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        // Covers gateway NotFoundException (no instance, 503) and unmatched-route 404,
        // both ResponseStatusException subclasses.
        if (ex instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
