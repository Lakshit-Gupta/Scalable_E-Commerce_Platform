package com.ecommerce.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Releases stock held by crashed/hung sagas (PENDING past TTL). Reuses the OutboxRelay poller pattern.
// ponytail: single-node poller; if product-service scales out, a shed-lock or DB advisory lock keeps
// one sweeper active — not needed until multiple replicas actually contend.
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationSweeper {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${reservation.sweep-ms:60000}")
    public void sweep() {
        int released = reservationService.releaseExpired(Instant.now());
        if (released > 0) {
            log.info("reservation sweeper released {} expired reservation(s)", released);
        }
    }
}
