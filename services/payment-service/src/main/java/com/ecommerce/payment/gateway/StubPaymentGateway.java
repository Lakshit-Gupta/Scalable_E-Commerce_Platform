package com.ecommerce.payment.gateway;

import com.ecommerce.payment.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dev/CI fallback when no STRIPE_SECRET_KEY is configured (v0.0.10). Auto-approves charges so the
 * platform runs end-to-end without external calls — mirrors the notification-service Resend fallback.
 */
@Slf4j
public class StubPaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "stub";

    @Override
    public GatewayCharge charge(String orderId, String userId, BigDecimal amount, String idempotencyKey) {
        String ref = "stub_" + UUID.randomUUID();
        log.info("[stub-gateway] auto-approving charge: order={} amount={} USD ref={}", orderId, amount, ref);
        return new GatewayCharge(PROVIDER, ref, PaymentStatus.SUCCESS);
    }
}
