package com.ecommerce.notification.service;

import com.ecommerce.notification.client.UserServiceClient;
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
 * Transactional email via Resend (v0.0.8). Resolves recipient email from user-service by
 * Keycloak subject (userId = sub injected by gateway). Degrades gracefully:
 *   - no RESEND_API_KEY   → logs what it would send (dev/CI never break)
 *   - email unresolvable  → logs + skips the real send
 * A real send failure is rethrown as {@link OrderEventConsumer.EmailException}.
 * {@code @Primary} so it is the active EmailService; the logging stub remains as a fallback bean.
 */
@Service
@Primary
@Slf4j
public class ResendEmailService implements OrderEventConsumer.EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String from;
    private final RestClient resendClient;
    private final UserServiceClient userServiceClient;

    public ResendEmailService(
            @Value("${RESEND_API_KEY:}") String apiKey,
            @Value("${RESEND_FROM:orders@example.com}") String from,
            UserServiceClient userServiceClient) {
        this.apiKey = apiKey;
        this.from = from;
        this.resendClient = RestClient.create();
        this.userServiceClient = userServiceClient;
    }

    @Override
    public void sendOrderConfirmation(String userId, UUID orderId) {
        String subject = "Your order " + orderId + " is confirmed";
        String html = "<p>Thanks! Your order <strong>" + orderId + "</strong> has been confirmed.</p>";

        if (apiKey == null || apiKey.isBlank()) {
            log.info("[EMAIL:log-fallback] no RESEND_API_KEY -> would send '{}' to user={} order={}",
                subject, userId, orderId);
            return;
        }

        String recipientEmail = userServiceClient.resolveEmail(userId).orElse(null);
        if (recipientEmail == null) {
            log.warn("[EMAIL:skip] could not resolve email for userId={} order={}", userId, orderId);
            return;
        }

        try {
            resendClient.post()
                .uri(RESEND_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", recipientEmail, "subject", subject, "html", html))
                .retrieve()
                .toBodilessEntity();
            log.info("[EMAIL:resend] sent order confirmation to {} order={}", recipientEmail, orderId);
        } catch (Exception e) {
            throw new OrderEventConsumer.EmailException("Resend send failed: " + e.getMessage());
        }
    }
}
