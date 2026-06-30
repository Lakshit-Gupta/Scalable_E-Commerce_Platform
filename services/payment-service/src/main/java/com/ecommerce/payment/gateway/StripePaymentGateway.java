package com.ecommerce.payment.gateway;

import com.ecommerce.payment.model.PaymentStatus;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Primary payment provider: Stripe (v0.0.10). Creates a confirmed PaymentIntent server-side using a
 * Stripe test payment method (real card data is tokenized client-side — never reaches us, PCI). The
 * order id is passed as Stripe's idempotency key so retries don't double-charge.
 */
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "stripe";

    public StripePaymentGateway(String apiKey) {
        Stripe.apiKey = apiKey;
    }

    @Override
    public GatewayCharge charge(String orderId, String userId, BigDecimal amount, String idempotencyKey) {
        long minorUnits = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(minorUnits)
            .setCurrency("usd")
            .setConfirm(true)
            .setPaymentMethod("pm_card_visa")   // Stripe test token; client tokenizes real cards (PCI)
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                    .build())
            .putMetadata("orderId", orderId)
            .putMetadata("userId", userId)
            .build();

        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();

        try {
            PaymentIntent intent = PaymentIntent.create(params, options);
            PaymentStatus status = mapStatus(intent.getStatus());
            log.info("[stripe] PaymentIntent {} for order {} -> {} ({})",
                intent.getId(), orderId, status, intent.getStatus());
            return new GatewayCharge(PROVIDER, intent.getId(), status);
        } catch (StripeException e) {
            log.error("[stripe] charge failed for order {}: {}", orderId, e.getMessage());
            return new GatewayCharge(PROVIDER, null, PaymentStatus.FAILED);
        }
    }

    private PaymentStatus mapStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.FAILED;
        }
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCESS;
            case "processing", "requires_action", "requires_capture",
                 "requires_confirmation", "requires_payment_method" -> PaymentStatus.PENDING;
            default -> PaymentStatus.FAILED;
        };
    }
}
