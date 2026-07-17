package com.ecommerce.notification.consumer;

import com.ecommerce.notification.analytics.PostHogAnalytics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka fan-out consumer (v0.0.11): subscribes to the order domain-event stream published by
 * order-service's outbox relay. Logs the event (audit) and forwards it to PostHog product analytics
 * (v0.0.17). Separate from the RabbitMQ work-queue that triggers the actual email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventsKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final PostHogAnalytics analytics;

    @KafkaListener(topics = "ecommerce.order-events", groupId = "${spring.kafka.consumer.group-id:notification}")
    public void onOrderEvent(String payload) {
        log.info("[kafka] order-events received: {}", payload);
        try {
            JsonNode node = objectMapper.readTree(payload);
            // Apicurio ExtJsonConverter wraps the event as {"schemaId":N,"payload":{...}}; the plain
            // outbox poller emits it at root. Unwrap when the envelope is present.
            JsonNode event = node.has("payload") ? node.get("payload") : node;
            Map<String, Object> props = new HashMap<>();
            props.put("orderId", event.path("orderId").asText());
            props.put("totalAmount", event.path("totalAmount").asDouble());
            analytics.capture(event.path("userId").asText(null), "order_placed", props);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Poison message: unparseable payload won't be fixed by retry — send to DLT immediately.
            log.error("[kafka] unparseable order event, will route to DLT: {}", e.getMessage());
            throw new IllegalArgumentException("Unparseable order event payload", e);
        }
    }
}
