package com.ecommerce.order.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.slf4j.MDC;

/**
 * Copies the current correlation id (set by common's servlet CorrelationIdFilter on the request
 * thread) onto outbound gRPC metadata, so payment-service shares the same id. Counterpart of
 * payment's GrpcServerCorrelationInterceptor.
 */
@GrpcGlobalClientInterceptor
public class GrpcClientCorrelationInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> CID_KEY =
        Metadata.Key.of("X-Correlation-Id", Metadata.ASCII_STRING_MARSHALLER);
    private static final String MDC_KEY = "correlationId";

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                String correlationId = MDC.get(MDC_KEY);
                if (correlationId != null) {
                    headers.put(CID_KEY, correlationId);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
