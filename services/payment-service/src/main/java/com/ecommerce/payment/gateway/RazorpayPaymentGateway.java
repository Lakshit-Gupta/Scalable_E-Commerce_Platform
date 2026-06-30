package com.ecommerce.payment.gateway;

import com.ecommerce.payment.model.PaymentStatus;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Secondary/regional payment provider: Razorpay (v0.0.15). Creates a Razorpay Order server-side
 * (the customer completes payment client-side; final settlement arrives via webhook). The order id
 * is the provider reference. No card data touches this service (PCI). {@code receipt} = our order id
 * gives Razorpay-side idempotency/traceability.
 */
@Slf4j
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "razorpay";

    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentGateway(String keyId, String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    @Override
    public GatewayCharge charge(String orderId, String userId, BigDecimal amount, String idempotencyKey) {
        long minorUnits = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject request = new JSONObject()
                .put("amount", minorUnits)
                .put("currency", "INR")
                .put("receipt", orderId)
                .put("payment_capture", true);

            Order order = client.orders.create(request);
            String providerRef = order.get("id");
            String rzpStatus = order.get("status");   // typically "created" until the customer pays
            PaymentStatus status = "paid".equals(rzpStatus) ? PaymentStatus.SUCCESS : PaymentStatus.PENDING;

            log.info("[razorpay] order {} for {} -> {} ({})", providerRef, orderId, status, rzpStatus);
            return new GatewayCharge(PROVIDER, providerRef, status);
        } catch (Exception e) {
            log.error("[razorpay] charge failed for order {}: {}", orderId, e.getMessage());
            return new GatewayCharge(PROVIDER, null, PaymentStatus.FAILED);
        }
    }
}
