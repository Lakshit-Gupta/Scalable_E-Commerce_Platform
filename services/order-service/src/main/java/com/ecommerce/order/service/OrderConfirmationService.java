package com.ecommerce.order.service;

import com.ecommerce.order.model.IdempotencyKey;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OutboxEvent;
import com.ecommerce.order.repository.IdempotencyKeyRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commits the CONFIRMED order and its {@code order.placed} outbox event in a single transaction
 * (v0.0.11) — the transactional-outbox guarantee. Kept separate from {@code OrderService} so the
 * gRPC payment call stays OUTSIDE any DB transaction. The relay publishes the staged row to Kafka.
 */
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

    static final String EVENT_TYPE = "order.placed";
    static final String AGGREGATE_TYPE = "order";

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // Persist the PENDING order shell and (if supplied) its idempotency key in ONE transaction, so a
    // key never maps to a missing order. saveAndFlush forces the PK constraint check now: a duplicate
    // key throws DataIntegrityViolationException, which OrderService catches to return the first order.
    @Transactional
    public void createPending(Order order, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyKeyRepository.saveAndFlush(
                new IdempotencyKey(idempotencyKey, order.getId(), Instant.now()));
        }
        orderRepository.save(order);
    }

    @Transactional
    public void confirmAndStage(Order order) {
        order.setStatus(Order.OrderStatus.CONFIRMED);
        orderRepository.save(order);
        outboxRepository.save(buildEvent(order));
    }

    private OutboxEvent buildEvent(Order order) {
        List<String> productIds = order.getItems() == null ? List.of()
            : order.getItems().stream().map(Order.OrderItem::getProductId).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("userId", order.getUserId());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("status", order.getStatus().name());
        payload.put("productIds", productIds);
        payload.put("placedAt", Instant.now().toString());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }

        return OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType(AGGREGATE_TYPE)
            .aggregateId(order.getId().toString())
            .eventType(EVENT_TYPE)
            .payload(json)
            .createdAt(Instant.now())
            .published(false)
            .build();
    }
}
