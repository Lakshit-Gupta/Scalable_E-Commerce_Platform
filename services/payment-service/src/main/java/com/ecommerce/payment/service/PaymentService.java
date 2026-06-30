package com.ecommerce.payment.service;

import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    /**
     * Idempotent charge keyed by orderId: retrying the same order returns the existing payment
     * instead of charging twice. Delegates the actual charge to the configured {@link PaymentGateway}
     * (Stripe in prod, stub in dev), passing orderId as the provider idempotency key. No card data is
     * ever stored (PCI) — only the provider's reference.
     */
    @Transactional
    public Payment charge(String userId, String orderId, BigDecimal amount) {
        return paymentRepository.findByOrderId(orderId).orElseGet(() -> {
            PaymentGateway.GatewayCharge result = paymentGateway.charge(orderId, userId, amount, orderId);
            Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .status(result.status())
                .provider(result.provider())
                .providerRef(result.providerRef())
                .createdAt(Instant.now())
                .build();
            return paymentRepository.save(payment);
        });
    }

    /**
     * Settle a payment from a verified provider webhook (v0.0.14): match by the provider reference
     * (Stripe PaymentIntent id) and set the final status. Idempotent — re-applying the same status
     * is a no-op. Returns false if no matching payment is found.
     */
    @Transactional
    public boolean markByProviderRef(String providerRef, PaymentStatus status) {
        return paymentRepository.findByProviderRef(providerRef)
            .map(payment -> {
                payment.setStatus(status);
                paymentRepository.save(payment);
                return true;
            })
            .orElse(false);
    }

    public PaymentStatus status(String paymentId) {
        try {
            return paymentRepository.findById(UUID.fromString(paymentId))
                .map(Payment::getStatus)
                .orElse(PaymentStatus.UNKNOWN);
        } catch (IllegalArgumentException badUuid) {
            return PaymentStatus.UNKNOWN;
        }
    }
}
