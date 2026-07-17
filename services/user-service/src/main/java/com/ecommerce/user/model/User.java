package com.ecommerce.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private UUID id;   // local profile UUID

    @Column(unique = true)
    private String keycloakId;   // Keycloak subject (sub) — used for X-User-Id lookups

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String passwordHash;   // null for Keycloak-backed users

    private String role;   // CUSTOMER / ADMIN

    @Column(nullable = true)
    private String phoneNumber;   // E.164 phone for SMS notifications (v0.1.3); nullable

    private Instant createdAt;
}
