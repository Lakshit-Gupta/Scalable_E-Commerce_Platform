package com.ecommerce.notification.service;

import com.ecommerce.notification.consumer.OrderEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Transactional email via Resend (v0.0.8) — supersedes the SendGrid/logging stub.
 *
 * Uses Spring's {@link RestClient} (no extra dependency) to POST to the Resend API. Degrades
 * gracefully:
 *   - no RESEND_API_KEY  -> logs what it would send (dev/CI never break);
 *   - user id is not an email -> logs + skips the real send (the order event currently carries only
 *     the user id; resolving the recipient against user-service is a later step).
 * A real send failure is rethrown as {@link OrderEventConsumer.EmailException} so the existing
 * RabbitMQ retry/DLQ handling in the consumer applies. {@code @Primary} so it is the active
 * EmailService (the logging stub remains as a fallback bean).
 */
@Service
@Primary
@Slf4j
public class ResendEmailService implements OrderEventConsumer.EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String from;
    private final RestClient restClient;

    public ResendEmailService(
            @Value("${RESEND_API_KEY:}") String apiKey,
            @Value("${RESEND_FROM:orders@example.com}") String from,
            RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.from = from;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void sendOrderConfirmation(String userId, UUID orderId) {
        String subject = "Your order " + orderId + " is confirmed";
        String html = "<p>Thanks! Your order <strong>" + orderId + "</strong> has been confirmed.</p>";

        if (apiKey == null || apiKey.isBlank()) {
            log.info("[EMAIL:log-fallback] no RESEND_API_KEY set -> would send '{}' to user={} order={}",
                subject, userId, orderId);
            return;
        }
        if (userId == null || !userId.contains("@")) {
            log.info("[EMAIL:skip] user id '{}' is not an email; skipping real send for order={}", userId, orderId);
            return;
        }
        try {
            restClient.post()
                .uri(RESEND_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", userId, "subject", subject, "html", html))
                .retrieve()
                .toBodilessEntity();
            log.info("[EMAIL:resend] sent order confirmation to {} order={}", userId, orderId);
        } catch (Exception e) {
            throw new OrderEventConsumer.EmailException("Resend send failed: " + e.getMessage());
        }
    }
}
