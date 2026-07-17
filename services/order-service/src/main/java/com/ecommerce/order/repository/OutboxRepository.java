package com.ecommerce.order.repository;

import com.ecommerce.order.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest-first batch: unpublished and not permanently failed. */
    List<OutboxEvent> findTop100ByPublishedFalseAndFailedFalseOrderByCreatedAtAsc();
}
