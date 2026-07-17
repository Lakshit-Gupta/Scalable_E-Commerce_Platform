package com.ecommerce.order.service;

import com.ecommerce.order.model.IdempotencyKey;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.IdempotencyKeyRepository;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Idempotency replay: a request whose key already exists returns the first order and never re-runs
// the saga (no second reserve, charge, or order). Replay returns before any gRPC field is touched.
@ExtendWith(MockitoExtension.class)
class OrderServiceIdempotencyTest {

    @Mock OrderRepository orderRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock OrderConfirmationService orderConfirmationService;

    private OrderService newService() {
        // Only the collaborators the replay path uses need to be real mocks; the rest can be bare.
        return new OrderService(orderRepository, idempotencyKeyRepository,
            mock(com.ecommerce.order.publisher.OrderEventPublisher.class),
            orderConfirmationService,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            mock(org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory.class));
    }

    @Test
    void replay_returnsExistingOrder_andSkipsSaga() {
        String userId = UUID.randomUUID().toString();
        String clientKey = "cart-abc";
        String scopedKey = userId + "|" + clientKey;   // namespaced by user server-side
        UUID orderId = UUID.randomUUID();
        Order existing = Order.builder().id(orderId).userId(userId)
            .status(Order.OrderStatus.CONFIRMED).totalAmount(BigDecimal.TEN).build();

        when(idempotencyKeyRepository.findById(scopedKey))
            .thenReturn(Optional.of(new IdempotencyKey(scopedKey, orderId, Instant.now())));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(existing));

        var req = new OrderService.CreateOrderRequest();
        req.setTotalAmount(BigDecimal.TEN);

        Order result = newService().placeOrder(req, userId, clientKey);

        assertThat(result).isSameAs(existing);
        // Saga never started: no new PENDING order / key written.
        verify(orderConfirmationService, never()).createPending(any(), any());
    }
}
