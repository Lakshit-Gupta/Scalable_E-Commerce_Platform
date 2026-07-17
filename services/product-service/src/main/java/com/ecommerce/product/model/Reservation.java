package com.ecommerce.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// One row per (order line) stock hold. Stock is decremented on insert (PENDING) and restored on
// RELEASED; CONFIRMED rows are permanent (payment settled). Keyed/queried by orderId.
@Entity
@Table(name = "reservations", indexes = {
    @Index(name = "idx_res_order", columnList = "orderId"),
    @Index(name = "idx_res_sweep", columnList = "status,expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    @Id
    private UUID id;
    private UUID orderId;
    private UUID productId;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant expiresAt;
    private Instant createdAt;

    public enum Status { PENDING, CONFIRMED, RELEASED }
}
