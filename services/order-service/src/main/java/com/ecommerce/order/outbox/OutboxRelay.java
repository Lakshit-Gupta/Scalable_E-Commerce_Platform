package com.ecommerce.order.outbox;

import com.ecommerce.order.model.OutboxEvent;
import com.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polling publisher (v0.0.11): periodically relays unpublished outbox rows to Kafka, then marks them
 * published. At-least-once — a crash between the Kafka send and the DB mark re-sends on the next run,
 * so downstream consumers must be idempotent. (Debezium CDC can replace this poller later.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    /** Domain-event stream consumed by analytics / search indexing / notifications / audit. */
    public static final String TOPIC = "ecommerce.order-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload()).get();
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                log.info("[outbox->kafka] {} {} -> {}", event.getEventType(), event.getAggregateId(), TOPIC);
            } catch (Exception ex) {
                // Broker hiccup: leave this and the rest unpublished; the next run retries in order.
                log.warn("[outbox->kafka] publish failed for {} (will retry): {}", event.getId(), ex.toString());
                break;
            }
        }
    }
}
