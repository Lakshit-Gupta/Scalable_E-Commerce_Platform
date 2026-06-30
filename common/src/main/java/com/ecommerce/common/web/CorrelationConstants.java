package com.ecommerce.common.web;

/** Shared names for correlation-id propagation across HTTP and logging (MDC). */
public final class CorrelationConstants {

    /** Inbound/outbound HTTP header carrying the correlation id. */
    public static final String HEADER = "X-Correlation-Id";

    /** SLF4J MDC key; reference as {@code %X{correlationId}} in log patterns. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationConstants() {
    }
}
