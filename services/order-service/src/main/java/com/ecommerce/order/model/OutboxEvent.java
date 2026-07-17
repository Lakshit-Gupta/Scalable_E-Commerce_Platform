package com.ecommerce.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row (v0.0.11). Written in the SAME DB transaction as the order state change,
 * so a domain event is never lost and never published for an order that didn't commit. A scheduled
 * relay ({@code OutboxRelay}) publishes unpublished rows to Kafka (at-least-once). Debezium CDC can
 * later replace the relay without touching producers.
 */
@Entity
@Table(name = "outbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    private String aggregateType;   // e.g. "order"

    private String aggregateId;     // order id (used as the Kafka message key)

    private String eventType;       // e.g. "order.placed"

    @Column(columnDefinition = "text")
    private String payload;         // event body as JSON

    private Instant createdAt;

    private boolean published;

    private Instant publishedAt;

    private int retryCount;   // incremented each time the relay fails to publish this event

    private boolean failed;   // true when retryCount exceeds the max (event skipped by relay)
}
