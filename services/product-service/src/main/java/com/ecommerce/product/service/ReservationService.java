package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.Reservation;
import com.ecommerce.product.repository.jpa.ProductRepository;
import com.ecommerce.product.repository.jpa.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Stock reservation saga participant. All methods idempotent and keyed by orderId so order-service
// retries (and the sweeper) are safe. Stock lives on Product.stockQuantity (authoritative, Postgres).
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    @Value("${reservation.ttl-minutes:15}")
    private long ttlMinutes;

    public record Line(UUID productId, int quantity) {}
    public record Result(boolean ok, String reason) {}

    // Reserve every line atomically (all-or-nothing). Idempotent: an order already holding PENDING/
    // CONFIRMED rows returns ok without decrementing again.
    @Transactional
    public Result reserve(UUID orderId, List<Line> lines) {
        if (!reservationRepository.findByOrderId(orderId).isEmpty()) {
            return new Result(true, "already reserved");
        }
        if (lines == null || lines.isEmpty()) {
            return new Result(true, "no lines");
        }
        // Lock products in a deterministic order to avoid deadlocks between concurrent multi-line orders.
        var sorted = lines.stream().sorted((a, b) -> a.productId().compareTo(b.productId())).toList();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));
        for (Line line : sorted) {
            if (line.quantity() <= 0) {
                throw new IllegalStateException("reservation quantity must be positive");
            }
            Product product = productRepository.findByIdForUpdate(line.productId()).orElse(null);
            if (product == null) {
                throw new ProductMissing(line.productId());   // rolls back the whole reservation
            }
            if (product.getStockQuantity() < line.quantity()) {
                throw new OutOfStock(line.productId());        // rolls back the whole reservation
            }
            product.setStockQuantity(product.getStockQuantity() - line.quantity());
            reservationRepository.save(Reservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .productId(line.productId())
                .quantity(line.quantity())
                .status(Reservation.Status.PENDING)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());
        }
        return new Result(true, "reserved");
    }

    // Payment settled: PENDING -> CONFIRMED (stock stays decremented). No-op if none/already confirmed.
    @Transactional
    public void confirm(UUID orderId) {
        for (Reservation r : reservationRepository.findByOrderIdAndStatus(orderId, Reservation.Status.PENDING)) {
            r.setStatus(Reservation.Status.CONFIRMED);
        }
    }

    // Compensation: PENDING -> RELEASED and restore stock. Never touches CONFIRMED. Idempotent.
    @Transactional
    public void release(UUID orderId) {
        releaseAll(reservationRepository.findByOrderIdAndStatus(orderId, Reservation.Status.PENDING));
    }

    // Sweeper entrypoint: release everything past its TTL (crashed/hung sagas). Batched by the caller.
    @Transactional
    public int releaseExpired(Instant now) {
        List<Reservation> expired = reservationRepository.findExpired(now);
        releaseAll(expired);
        return expired.size();
    }

    private void releaseAll(List<Reservation> pending) {
        // Aggregate per product so one locked read restores all of that product's held units.
        Map<UUID, Integer> byProduct = new java.util.HashMap<>();
        for (Reservation r : pending) {
            byProduct.merge(r.getProductId(), r.getQuantity(), Integer::sum);
            r.setStatus(Reservation.Status.RELEASED);
        }
        byProduct.forEach((productId, qty) ->
            productRepository.findByIdForUpdate(productId).ifPresent(p ->
                p.setStockQuantity(p.getStockQuantity() + qty)));
    }

    public static class OutOfStock extends RuntimeException {
        public OutOfStock(UUID productId) { super("insufficient stock for product " + productId); }
    }
    public static class ProductMissing extends RuntimeException {
        public ProductMissing(UUID productId) { super("unknown product " + productId); }
    }
}
