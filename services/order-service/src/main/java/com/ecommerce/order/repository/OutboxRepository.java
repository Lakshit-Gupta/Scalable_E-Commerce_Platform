package com.ecommerce.order.repository;

import com.ecommerce.order.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest-first batch of not-yet-published events for the relay to publish. */
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
