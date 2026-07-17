package com.ecommerce.order.outbox;

import com.ecommerce.order.model.OutboxEvent;
import com.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polling publisher fallback: relays unpublished outbox rows to Kafka (at-least-once).
 * Active only when {@code outbox.relay.enabled=true}. Disabled in the full stack (default) because
 * Debezium CDC (kafka-connect service) handles publishing directly from the PostgreSQL WAL.
 * Enable for dev/test environments running without Debezium.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    public static final String TOPIC = "ecommerce.order-events";
    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedFalseAndFailedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()).get();
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                log.info("[outbox->kafka] {} {} -> {}", event.getEventType(), event.getAggregateId(), TOPIC);
            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                if (retries >= MAX_RETRIES) {
                    event.setFailed(true);
                    log.error("[outbox->kafka] event {} marked failed after {} retries: {}",
                        event.getId(), retries, ex.toString());
                } else {
                    log.warn("[outbox->kafka] publish failed for {} (attempt {}/{}): {}",
                        event.getId(), retries, MAX_RETRIES, ex.toString());
                }
                outboxRepository.save(event);
                // Continue to next event — don't let one stuck row block the batch.
            }
        }
    }
}
