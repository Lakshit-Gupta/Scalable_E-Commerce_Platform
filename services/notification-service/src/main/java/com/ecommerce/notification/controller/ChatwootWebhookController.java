package com.ecommerce.notification.controller;

import com.ecommerce.notification.analytics.PostHogAnalytics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Chatwoot inbound webhook receiver (v0.1.2). PUBLIC route (no JWT) — Chatwoot posts account/inbox
 * events here (conversation created, status changed, message created…). Authenticity is a shared
 * secret (`CHATWOOT_WEBHOOK_TOKEN`) sent as the {@code X-Chatwoot-Token} header: when the secret is
 * configured, a mismatch is rejected 401; when unset the event is accepted and logged (dev
 * convenience, matching the graceful-degradation convention used elsewhere).
 *
 * Handling is app-level and infra-free: structured log + a best-effort PostHog capture so support
 * activity shows up alongside product analytics. Reuses {@link PostHogAnalytics} (no-op without a key).
 */
@RestController
@RequestMapping("/support/chatwoot")
@RequiredArgsConstructor
@Slf4j
public class ChatwootWebhookController {

    private final PostHogAnalytics postHog;
    private final ObjectMapper objectMapper;

    @Value("${CHATWOOT_WEBHOOK_TOKEN:}")
    private String webhookToken;

    @PostMapping("/webhook")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "X-Chatwoot-Token", required = false) String token) {
        if (webhookToken != null && !webhookToken.isBlank() && !webhookToken.equals(token)) {
            log.warn("[chatwoot-webhook] rejected: X-Chatwoot-Token mismatch");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid token");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("unknown");
            String conversationId = firstNonBlank(
                root.path("conversation").path("id").asText(null),
                root.path("id").asText(null));
            String status = firstNonBlank(
                root.path("conversation").path("status").asText(null),
                root.path("status").asText(null));
            String distinctId = extractIdentifier(root);

            log.info("[chatwoot-webhook] event={} conversation={} status={} contact={}",
                event, conversationId, status, distinctId);

            Map<String, Object> props = new HashMap<>();
            props.put("event", event);
            if (conversationId != null) props.put("conversation_id", conversationId);
            if (status != null) props.put("status", status);
            props.put("source", "chatwoot");
            postHog.capture(distinctId, "support_" + event, props);

            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            // Never make Chatwoot retry-storm on a malformed body; log and accept.
            log.warn("[chatwoot-webhook] could not parse payload: {}", e.getMessage());
            return ResponseEntity.ok("accepted");
        }
    }

    /** Best-effort visitor id from the various shapes Chatwoot uses across event types. */
    private String extractIdentifier(JsonNode root) {
        String[] candidates = {
            root.path("sender").path("identifier").asText(null),
            root.path("sender").path("email").asText(null),
            root.path("contact").path("identifier").asText(null),
            root.path("contact").path("email").asText(null),
            root.path("meta").path("sender").path("identifier").asText(null),
            root.path("meta").path("sender").path("email").asText(null)
        };
        return firstNonBlank(candidates);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
