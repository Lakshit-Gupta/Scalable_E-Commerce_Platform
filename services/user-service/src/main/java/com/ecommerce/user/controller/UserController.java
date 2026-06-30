package com.ecommerce.user.controller;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /** Profile of the caller. X-User-Id is injected by the gateway after JWT validation. */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("X-User-Id") String userId) {
        return userRepository.findById(UUID.fromString(userId))
            .<ResponseEntity<?>>map(this::toProfile)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> toProfile(User user) {
        return ResponseEntity.ok(Map.of(
            "id", user.getId().toString(),
            "email", user.getEmail(),
            "role", user.getRole() == null ? "CUSTOMER" : user.getRole()
        ));
    }
}
