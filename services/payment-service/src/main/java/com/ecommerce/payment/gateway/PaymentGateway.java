package com.ecommerce.payment.gateway;

import com.ecommerce.payment.model.PaymentStatus;

import java.math.BigDecimal;

/**
 * Provider-agnostic payment abstraction (v0.0.10). Adapters: {@code StripePaymentGateway} (primary)
 * and {@code StubPaymentGateway} (dev fallback). No card data ever touches this service — providers
 * tokenize at the edge (PCI). {@code idempotencyKey} (the order id) makes retries safe.
 */
public interface PaymentGateway {

    GatewayCharge charge(String orderId, String userId, BigDecimal amount, String idempotencyKey);

    /** Outcome of a charge attempt: which provider handled it, its reference, and the mapped status. */
    record GatewayCharge(String provider, String providerRef, PaymentStatus status) {
    }
}
