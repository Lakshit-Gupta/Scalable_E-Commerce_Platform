package com.ecommerce.payment.controller;

import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.model.ProcessedWebhookEvent;
import com.ecommerce.payment.repository.ProcessedWebhookEventRepository;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Stripe webhook receiver for async settlement (v0.0.14). PUBLIC route (no JWT) — authenticity is
 * established by HMAC signature verification, not a bearer token. Idempotent: redelivered events
 * (same id) are skipped. Reads the RAW body (signature is over exact bytes).
 */
@RestController
@RequestMapping("/payments/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final ProcessedWebhookEventRepository processedRepository;
    private final ObjectMapper objectMapper;

    @Value("${STRIPE_WEBHOOK_SECRET:}")
    private String webhookSecret;

    @PostMapping("/stripe")
    @Transactional
    public ResponseEntity<String> handleStripe(@RequestBody String payload,
                                               @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[stripe-webhook] STRIPE_WEBHOOK_SECRET unset — event ignored");
            return ResponseEntity.ok("ignored: no webhook secret configured");
        }
        try {
            Webhook.constructEvent(payload, signature, webhookSecret);   // verifies signature + timestamp
        } catch (SignatureVerificationException e) {
            log.warn("[stripe-webhook] signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid payload");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.path("id").asText();
            String type = root.path("type").asText();
            String paymentIntentId = root.path("data").path("object").path("id").asText();

            if (eventId.isBlank() || processedRepository.existsById(eventId)) {
                return ResponseEntity.ok("ignored: duplicate or missing id");
            }

            switch (type) {
                case "payment_intent.succeeded" ->
                    log.info("[stripe-webhook] {} pi={} -> SUCCESS (matched={})",
                        type, paymentIntentId, paymentService.markByProviderRef(paymentIntentId, PaymentStatus.SUCCESS));
                case "payment_intent.payment_failed" ->
                    log.info("[stripe-webhook] {} pi={} -> FAILED (matched={})",
                        type, paymentIntentId, paymentService.markByProviderRef(paymentIntentId, PaymentStatus.FAILED));
                default -> log.info("[stripe-webhook] ignoring unhandled type {}", type);
            }

            processedRepository.save(new ProcessedWebhookEvent(eventId, type, Instant.now()));
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("[stripe-webhook] processing error: {}", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing error");
        }
    }
}
