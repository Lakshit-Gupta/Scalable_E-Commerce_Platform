package com.ecommerce.user.grpc;

import com.ecommerce.grpc.user.GetEmailRequest;
import com.ecommerce.grpc.user.GetEmailResponse;
import com.ecommerce.grpc.user.UserServiceGrpc;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getEmail(GetEmailRequest request, StreamObserver<GetEmailResponse> responseObserver) {
        Optional<User> user = userRepository.findByKeycloakId(request.getKeycloakId());
        responseObserver.onNext(GetEmailResponse.newBuilder()
            .setEmail(user.map(User::getEmail).orElse(""))
            .setFound(user.isPresent())
            .setPhone(user.map(User::getPhoneNumber).orElse(""))   // for SMS (v0.1.3)
            .build());
        responseObserver.onCompleted();
    }
}
