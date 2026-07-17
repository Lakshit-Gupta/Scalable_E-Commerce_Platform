package com.ecommerce.recommendation.consumer;

import com.ecommerce.recommendation.service.RecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes the order-event stream (v0.0.12) and feeds purchases into the recommendation aggregates.
 * Own consumer group so it receives every event independently of notification-service (fan-out).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventsConsumer {

    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ecommerce.order-events", groupId = "${spring.kafka.consumer.group-id:recommendation}")
    public void onOrderEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            // Apicurio ExtJsonConverter wraps the event as {"schemaId":N,"payload":{...}}; the plain
            // outbox poller emits it at root. Unwrap when the envelope is present.
            JsonNode event = node.has("payload") ? node.get("payload") : node;
            String userId = event.path("userId").asText(null);
            List<String> productIds = new ArrayList<>();
            JsonNode ids = event.get("productIds");
            if (ids != null && ids.isArray()) {
                ids.forEach(n -> productIds.add(n.asText()));
            }
            recommendationService.recordPurchase(userId, productIds);
            log.info("[reco] processed order {} ({} products) user={}", event.path("orderId").asText(), productIds.size(), userId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[reco] unparseable order event, routing to DLT: {}", e.getMessage());
            throw new IllegalArgumentException("Unparseable order event payload", e);
        }
    }
}
