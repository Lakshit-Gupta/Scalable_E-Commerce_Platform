package com.ecommerce.user.service;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.UnauthorizedException;
import com.ecommerce.user.model.RefreshToken;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.RefreshTokenRepository;
import com.ecommerce.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RSAPrivateKey privateKey;             // RS256 signing key
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private static final long ACCESS_TTL_SECONDS = 900; // 15 minutes

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            throw new ConflictException("Email already registered");
        });
        User user = User.builder()
            .id(UUID.randomUUID())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role("CUSTOMER")
            .createdAt(Instant.now())
            .build();
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        refreshTokenRepository.delete(stored);   // rotation: old token is single-use
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = Jwts.builder()
            .setSubject(user.getId().toString())
            .claim("role", user.getRole())
            .claim("email", user.getEmail())
            .setIssuedAt(new Date())
            .setExpiration(Date.from(Instant.now().plusSeconds(ACCESS_TTL_SECONDS)))
            .signWith(privateKey, SignatureAlgorithm.RS256)
            .compact();

        String refreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
            .token(refreshToken)
            .userId(user.getId())
            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .build());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(ACCESS_TTL_SECONDS)
            .build();
    }

    @Data
    public static class RegisterRequest {
        @Email @NotBlank private String email;
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters")
        private String password;
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
