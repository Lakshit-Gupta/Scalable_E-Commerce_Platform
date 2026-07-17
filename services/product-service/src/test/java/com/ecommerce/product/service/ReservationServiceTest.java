package com.ecommerce.product.service;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.Reservation;
import com.ecommerce.product.repository.jpa.ProductRepository;
import com.ecommerce.product.repository.jpa.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Stock/money path — the branch logic that must not break. Repos mocked; no Spring context.
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks ReservationService service;

    UUID productId;
    UUID orderId;
    Product product;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        product = Product.builder().id(productId).stockQuantity(10).build();
        lenient().when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
    }

    private List<ReservationService.Line> line(int qty) {
        return List.of(new ReservationService.Line(productId, qty));
    }

    @Test
    void reserve_decrementsStock_andSavesPending() {
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of());

        var result = service.reserve(orderId, line(3));

        assertThat(result.ok()).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void reserve_insufficientStock_throwsToRollBack_andDoesNotDecrement() {
        // Throwing (not returning ok=false) is what rolls back the @Transactional for all-or-nothing;
        // the gRPC layer catches it and reports ok=false to order-service.
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.reserve(orderId, line(11)))   // only 10 in stock
            .isInstanceOf(ReservationService.OutOfStock.class);

        assertThat(product.getStockQuantity()).isEqualTo(10);   // untouched
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_isIdempotent_whenOrderAlreadyReserved() {
        when(reservationRepository.findByOrderId(orderId))
            .thenReturn(List.of(Reservation.builder().orderId(orderId).build()));

        var result = service.reserve(orderId, line(3));

        assertThat(result.ok()).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(10);   // no second decrement
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void release_restoresStock_forPendingOnly() {
        var pending = Reservation.builder().orderId(orderId).productId(productId).quantity(4)
            .status(Reservation.Status.PENDING).build();
        when(reservationRepository.findByOrderIdAndStatus(orderId, Reservation.Status.PENDING))
            .thenReturn(List.of(pending));

        service.release(orderId);

        assertThat(pending.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(product.getStockQuantity()).isEqualTo(14);   // 10 + 4 restored
    }

    @Test
    void confirm_keepsStockDecremented() {
        var pending = Reservation.builder().orderId(orderId).productId(productId).quantity(4)
            .status(Reservation.Status.PENDING).build();
        when(reservationRepository.findByOrderIdAndStatus(orderId, Reservation.Status.PENDING))
            .thenReturn(List.of(pending));

        service.confirm(orderId);

        assertThat(pending.getStatus()).isEqualTo(Reservation.Status.CONFIRMED);
        assertThat(product.getStockQuantity()).isEqualTo(10);   // NOT restored
    }

    @Test
    void sweeper_releasesExpiredPending() {
        var expired = Reservation.builder().orderId(orderId).productId(productId).quantity(2)
            .status(Reservation.Status.PENDING).build();
        Instant now = Instant.now();
        when(reservationRepository.findExpired(now)).thenReturn(List.of(expired));

        int released = service.releaseExpired(now);

        assertThat(released).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(product.getStockQuantity()).isEqualTo(12);   // 10 + 2 restored
    }
}
