package com.ecommerce.notification.service;

import com.ecommerce.notification.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Transactional SMS via Twilio (v0.1.3). Sends an order-confirmation text alongside the Resend email.
 * Resolves the recipient phone from user-service by Keycloak subject. Best-effort — never throws, so
 * SMS problems never block the order-notification ack. Degrades gracefully:
 *   - no TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM → logs what it would send (dev/CI safe)
 *   - phone unresolvable                                      → logs + skips the real send
 */
@Service
@Slf4j
public class TwilioSmsService {

    private static final String TWILIO_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    private final String accountSid;
    private final String authToken;
    private final String from;
    private final RestClient twilioClient;
    private final UserServiceClient userServiceClient;

    public TwilioSmsService(
            @Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
            @Value("${TWILIO_AUTH_TOKEN:}") String authToken,
            @Value("${TWILIO_FROM:}") String from,
            UserServiceClient userServiceClient) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.twilioClient = RestClient.create();
        this.userServiceClient = userServiceClient;
    }

    private boolean configured() {
        return accountSid != null && !accountSid.isBlank()
            && authToken != null && !authToken.isBlank()
            && from != null && !from.isBlank();
    }

    public void sendOrderConfirmation(String userId, UUID orderId) {
        String body = "Your order " + orderId + " is confirmed. Thanks for shopping with us!";

        if (!configured()) {
            log.info("[SMS:log-fallback] Twilio not configured -> would send '{}' to user={} order={}",
                body, userId, orderId);
            return;
        }

        String phone = userServiceClient.resolvePhone(userId).orElse(null);
        if (phone == null) {
            log.warn("[SMS:skip] no phone on file for userId={} order={}", userId, orderId);
            return;
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", phone);
            form.add("From", from);
            form.add("Body", body);

            String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

            twilioClient.post()
                .uri(TWILIO_BASE + accountSid + "/Messages.json")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
            log.info("[SMS:twilio] sent order confirmation to {} order={}", phone, orderId);
        } catch (Exception e) {
            // Best-effort: log and swallow so the email/ack path is unaffected.
            log.warn("[SMS:twilio] send failed for order={}: {}", orderId, e.getMessage());
        }
    }
}
