package com.ecommerce.payment.grpc;

import com.ecommerce.grpc.payment.ChargeRequest;
import com.ecommerce.grpc.payment.ChargeResponse;
import com.ecommerce.grpc.payment.PaymentServiceGrpc;
import com.ecommerce.grpc.payment.StatusRequest;
import com.ecommerce.grpc.payment.StatusResponse;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.service.PaymentService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

/**
 * gRPC entry point — delegates to the same {@link PaymentService} used by the REST controller.
 * Replaces the old Feign/REST call from order-service.
 */
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentService paymentService;

    @Override
    public void charge(ChargeRequest request, StreamObserver<ChargeResponse> responseObserver) {
        Payment payment = paymentService.charge(
            request.getUserId(),
            request.getOrderId(),
            new BigDecimal(request.getAmount()));
        responseObserver.onNext(ChargeResponse.newBuilder()
            .setPaymentId(payment.getId().toString())
            .setStatus(payment.getStatus().name())
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        responseObserver.onNext(StatusResponse.newBuilder()
            .setStatus(paymentService.status(request.getPaymentId()).name())
            .build());
        responseObserver.onCompleted();
    }
}
