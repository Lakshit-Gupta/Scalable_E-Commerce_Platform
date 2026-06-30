package com.ecommerce.order.config;

import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcClientInterceptor;
import io.micrometer.observation.ObservationRegistry;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed tracing across the order -> payment gRPC hop (v0.0.7).
 *
 * Registers a global gRPC client interceptor that opens a client span and injects the W3C trace
 * context (traceparent) into outbound gRPC metadata, so payment-service continues the SAME trace.
 * Complements the existing correlation-id interceptor (which carries X-Correlation-Id for logs).
 */
@Configuration
public class GrpcTracingConfig {

    @GrpcGlobalClientInterceptor
    ClientInterceptor observationGrpcClientInterceptor(ObservationRegistry observationRegistry) {
        return new ObservationGrpcClientInterceptor(observationRegistry);
    }
}
