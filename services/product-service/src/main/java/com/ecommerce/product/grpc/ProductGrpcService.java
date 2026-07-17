package com.ecommerce.product.grpc;

import com.ecommerce.grpc.product.Ack;
import com.ecommerce.grpc.product.CheckExistsRequest;
import com.ecommerce.grpc.product.CheckExistsResponse;
import com.ecommerce.grpc.product.OrderRef;
import com.ecommerce.grpc.product.ProductServiceGrpc;
import com.ecommerce.grpc.product.ReserveRequest;
import com.ecommerce.grpc.product.ReserveResponse;
import com.ecommerce.product.repository.jpa.ProductRepository;
import com.ecommerce.product.service.ReservationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductRepository productRepository;
    private final ReservationService reservationService;

    @Override
    public void checkExists(CheckExistsRequest request, StreamObserver<CheckExistsResponse> responseObserver) {
        boolean exists;
        try {
            exists = productRepository.existsById(UUID.fromString(request.getProductId()));
        } catch (IllegalArgumentException e) {
            exists = false;
        }
        responseObserver.onNext(CheckExistsResponse.newBuilder().setExists(exists).build());
        responseObserver.onCompleted();
    }

    @Override
    public void reserve(ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
        ReserveResponse response;
        try {
            List<ReservationService.Line> lines = request.getLinesList().stream()
                .map(l -> new ReservationService.Line(UUID.fromString(l.getProductId()), l.getQuantity()))
                .toList();
            ReservationService.Result result =
                reservationService.reserve(UUID.fromString(request.getOrderId()), lines);
            response = ReserveResponse.newBuilder().setOk(result.ok()).setReason(result.reason()).build();
        } catch (ReservationService.OutOfStock | ReservationService.ProductMissing e) {
            // Business rejection — not a transport error. Order-service maps ok=false to 409.
            response = ReserveResponse.newBuilder().setOk(false).setReason(e.getMessage()).build();
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void confirm(OrderRef request, StreamObserver<Ack> responseObserver) {
        reservationService.confirm(UUID.fromString(request.getOrderId()));
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void release(OrderRef request, StreamObserver<Ack> responseObserver) {
        reservationService.release(UUID.fromString(request.getOrderId()));
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }
}
