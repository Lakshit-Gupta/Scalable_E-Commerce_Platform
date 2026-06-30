package com.ecommerce.payment.config;

import io.grpc.ServerInterceptor;
import io.micrometer.core.instrument.binder.grpc.ObservationGrpcServerInterceptor;
import io.micrometer.observation.ObservationRegistry;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

/**
 * Server side of the distributed trace for the order -> payment gRPC hop (v0.0.7).
 *
 * Registers a global gRPC server interceptor that extracts the W3C trace context (traceparent) from
 * inbound gRPC metadata and continues the caller's trace as a server span. Mirror of order-service's
 * ObservationGrpcClientInterceptor; complements the existing correlation-id server interceptor.
 */
@Configuration
public class GrpcTracingConfig {

    @GrpcGlobalServerInterceptor
    ServerInterceptor observationGrpcServerInterceptor(ObservationRegistry observationRegistry) {
        return new ObservationGrpcServerInterceptor(observationRegistry);
    }
}
