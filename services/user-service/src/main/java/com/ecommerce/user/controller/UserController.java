package com.ecommerce.user.controller;

import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /** Profile of the caller. X-User-Id = Keycloak subject injected by the gateway. */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("X-User-Id") String userId) {
        return userRepository.findByKeycloakId(userId)
            .<ResponseEntity<?>>map(this::toProfile)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Update the caller's profile (currently the SMS phone number, v0.1.3). */
    @PutMapping("/me")
    @Transactional
    public ResponseEntity<?> updateMe(@RequestHeader("X-User-Id") String userId,
                                      @RequestBody UpdateProfileRequest body) {
        User user = userRepository.findByKeycloakId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        if (body.getPhone() != null) {
            user.setPhoneNumber(body.getPhone().isBlank() ? null : body.getPhone());
        }
        userRepository.save(user);
        return toProfile(user);
    }

    /** Internal service-to-service endpoint: resolve email by Keycloak subject. Not exposed via gateway. */
    @GetMapping("/internal/{keycloakId}/email")
    public ResponseEntity<String> emailByKeycloakId(@PathVariable String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .<ResponseEntity<String>>map(u -> ResponseEntity.ok(u.getEmail()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> toProfile(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId().toString());
        profile.put("email", user.getEmail());
        profile.put("role", user.getRole() == null ? "CUSTOMER" : user.getRole());
        profile.put("phone", user.getPhoneNumber());   // nullable
        return ResponseEntity.ok(profile);
    }

    @Data
    public static class UpdateProfileRequest {
        private String phone;   // E.164; blank clears it
    }
}
