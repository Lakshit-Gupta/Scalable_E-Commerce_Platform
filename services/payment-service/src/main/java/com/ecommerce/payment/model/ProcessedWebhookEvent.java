package com.ecommerce.payment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Records processed provider webhook event ids (v0.0.14) so redelivered webhooks are ignored —
 * exactly-once effect on top of Stripe's at-least-once delivery.
 */
@Entity
@Table(name = "processed_webhook_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedWebhookEvent {

    @Id
    private String eventId;   // Stripe event id (evt_...)

    private String type;

    private Instant processedAt;
}
