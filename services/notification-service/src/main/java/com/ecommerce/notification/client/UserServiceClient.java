package com.ecommerce.notification.client;

import com.ecommerce.grpc.user.GetEmailRequest;
import com.ecommerce.grpc.user.GetEmailResponse;
import com.ecommerce.grpc.user.UserServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub stub;

    public Optional<String> resolveEmail(String keycloakId) {
        try {
            GetEmailResponse response = stub.getEmail(
                GetEmailRequest.newBuilder().setKeycloakId(keycloakId).build()
            );
            return response.getFound() ? Optional.of(response.getEmail()) : Optional.empty();
        } catch (Exception e) {
            log.error("[USER-GRPC] resolve email failed for keycloakId={}: {}", keycloakId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Resolve the SMS phone (E.164) for a Keycloak subject; empty when the user has none (v0.1.3). */
    public Optional<String> resolvePhone(String keycloakId) {
        try {
            GetEmailResponse response = stub.getEmail(
                GetEmailRequest.newBuilder().setKeycloakId(keycloakId).build()
            );
            String phone = response.getPhone();
            return (response.getFound() && phone != null && !phone.isBlank())
                ? Optional.of(phone) : Optional.empty();
        } catch (Exception e) {
            log.error("[USER-GRPC] resolve phone failed for keycloakId={}: {}", keycloakId, e.getMessage());
            return Optional.empty();
        }
    }
}
