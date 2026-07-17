package com.ecommerce.user.service;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.UnauthorizedException;
import com.ecommerce.user.client.KeycloakAdminClient;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already registered");
        }
        String firstName = request.getFirstName() != null && !request.getFirstName().isBlank()
            ? request.getFirstName() : request.getEmail().split("@")[0];
        String keycloakId = keycloakAdminClient.createUser(request.getEmail(), request.getPassword(), firstName, "");
        userRepository.save(User.builder()
            .id(UUID.randomUUID())
            .email(request.getEmail())
            .keycloakId(keycloakId)
            .role("CUSTOMER")
            .phoneNumber(request.getPhone())   // optional; used for SMS notifications (v0.1.3)
            .createdAt(Instant.now())
            .build());
        return toAuthResponse(keycloakAdminClient.getTokens(request.getEmail(), request.getPassword()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return toAuthResponse(keycloakAdminClient.getTokens(request.getEmail(), request.getPassword()));
    }

    public AuthResponse refresh(String refreshToken) {
        return toAuthResponse(keycloakAdminClient.refreshTokens(refreshToken));
    }

    private AuthResponse toAuthResponse(KeycloakAdminClient.TokenPair tokens) {
        return AuthResponse.builder()
            .accessToken(tokens.getAccessToken())
            .refreshToken(tokens.getRefreshToken())
            .expiresIn(tokens.getExpiresIn())
            .build();
    }

    @Data
    public static class RegisterRequest {
        @Email @NotBlank private String email;
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters")
        private String password;
        private String firstName;
        private String lastName;
        private String phone;   // optional E.164 phone for SMS notifications (v0.1.3)
    }

    @Data
    public static class LoginRequest {
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank private String refreshToken;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
    }
}
