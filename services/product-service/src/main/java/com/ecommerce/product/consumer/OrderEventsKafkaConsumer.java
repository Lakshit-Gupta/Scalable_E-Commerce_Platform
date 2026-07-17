package com.ecommerce.product.consumer;

import com.ecommerce.product.repository.search.ProductSearchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order events from the ecommerce.order-events Kafka topic and increments each
 * purchased product's {@code orderCount} in Elasticsearch. This keeps search ranking fresh
 * with real purchase signal: more-ordered products naturally surface higher in search results
 * via function_score boosting. Own consumer group — receives every event independently of
 * notification-service and recommendation-service (fan-out).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventsKafkaConsumer {

    private final ProductSearchRepository productSearchRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ecommerce.order-events", groupId = "product-search-indexing")
    public void onOrderEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            // Apicurio ExtJsonConverter wraps the event as {"schemaId":N,"payload":{...}}; the plain
            // outbox poller emits it at root. Unwrap when the envelope is present (matches the other consumers).
            JsonNode event = node.has("payload") ? node.get("payload") : node;
            JsonNode ids = event.get("productIds");
            if (ids == null || !ids.isArray() || ids.isEmpty()) {
                return;
            }
            ids.forEach(idNode -> {
                String productId = idNode.asText();
                productSearchRepository.findById(productId).ifPresent(doc -> {
                    doc.setOrderCount(doc.getOrderCount() + 1);
                    productSearchRepository.save(doc);
                    log.debug("[search-index] incremented orderCount for product={}", productId);
                });
            });
            log.info("[search-index] processed order {} ({} products)", event.path("orderId").asText(), ids.size());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[search-index] unparseable order event, routing to DLT: {}", e.getMessage());
            throw new IllegalArgumentException("Unparseable order event payload", e);
        }
    }
}
