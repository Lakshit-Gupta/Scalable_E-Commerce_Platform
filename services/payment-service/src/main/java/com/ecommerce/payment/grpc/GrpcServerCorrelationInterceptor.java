package com.ecommerce.payment.grpc;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.MDC;

/**
 * Reads the X-Correlation-Id from inbound gRPC metadata into the SLF4J MDC for the duration of the
 * unary call, so payment-service logs share the caller's correlation id. Mirror of the servlet
 * CorrelationIdFilter for the gRPC transport.
 */
@GrpcGlobalServerInterceptor
public class GrpcServerCorrelationInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> CID_KEY =
        Metadata.Key.of("X-Correlation-Id", Metadata.ASCII_STRING_MARSHALLER);
    private static final String MDC_KEY = "correlationId";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String correlationId = headers.get(CID_KEY);
        // For unary calls the handler runs within onHalfClose on this listener's thread.
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
            @Override
            public void onHalfClose() {
                if (correlationId != null) {
                    MDC.put(MDC_KEY, correlationId);
                }
                try {
                    super.onHalfClose();
                } finally {
                    MDC.remove(MDC_KEY);
                }
            }
        };
    }
}
