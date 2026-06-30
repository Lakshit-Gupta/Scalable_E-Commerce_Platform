package com.ecommerce.payment.config;

import com.ecommerce.payment.gateway.PaymentGateway;
import com.ecommerce.payment.gateway.RazorpayPaymentGateway;
import com.ecommerce.payment.gateway.StripePaymentGateway;
import com.ecommerce.payment.gateway.StubPaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the active payment provider at startup (v0.0.10, multi-provider v0.0.15):
 * {@code PAYMENT_PROVIDER} = stripe (primary, default) | razorpay (secondary/regional) | stub.
 * Falls back to the stub when the chosen provider's credentials are absent, so dev/CI always run.
 */
@Configuration
@Slf4j
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(
            @Value("${PAYMENT_PROVIDER:stripe}") String provider,
            @Value("${STRIPE_SECRET_KEY:}") String stripeApiKey,
            @Value("${RAZORPAY_KEY_ID:}") String razorpayKeyId,
            @Value("${RAZORPAY_KEY_SECRET:}") String razorpayKeySecret) {

        String selected = provider == null ? "stripe" : provider.trim().toLowerCase();

        switch (selected) {
            case "razorpay" -> {
                if (notBlank(razorpayKeyId) && notBlank(razorpayKeySecret)) {
                    log.info("Payment gateway: Razorpay (secondary)");
                    return new RazorpayPaymentGateway(razorpayKeyId, razorpayKeySecret);
                }
                log.warn("Payment gateway: PAYMENT_PROVIDER=razorpay but RAZORPAY_KEY_* unset — using STUB");
                return new StubPaymentGateway();
            }
            case "stripe" -> {
                if (notBlank(stripeApiKey)) {
                    log.info("Payment gateway: Stripe (primary)");
                    return new StripePaymentGateway(stripeApiKey);
                }
                log.warn("Payment gateway: PAYMENT_PROVIDER=stripe but STRIPE_SECRET_KEY unset — using STUB");
                return new StubPaymentGateway();
            }
            default -> {
                log.warn("Payment gateway: STUB (PAYMENT_PROVIDER='{}') — charges auto-approved (dev only)", selected);
                return new StubPaymentGateway();
            }
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
