package com.ecommerce.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes a correlation id for every request so logs and downstream calls can be traced.
 *
 * <p>Reads the {@code X-Correlation-Id} header; if absent, generates a UUID. The id is placed in
 * the SLF4J {@link MDC} (key {@code correlationId}) for the duration of the request and echoed on
 * the response header. The gateway normally sets this header at the edge; this filter covers direct
 * calls and ensures the id is always present. Runs first so every later component sees the id.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationConstants.HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationConstants.MDC_KEY, correlationId);
        response.setHeader(CorrelationConstants.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Critical with pooled/virtual threads: never leak the id into the next request.
            MDC.remove(CorrelationConstants.MDC_KEY);
        }
    }
}
