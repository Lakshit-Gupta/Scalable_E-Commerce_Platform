package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// Maps a client-supplied Idempotency-Key to the order it created. PK on the key = the dedupe guard;
// a second request with the same key hits the unique constraint instead of placing another order.
@Entity
@Table(name = "idempotency_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey {
    @Id
    @Column(length = 200)
    private String keyValue;
    private UUID orderId;
    private Instant createdAt;
}
