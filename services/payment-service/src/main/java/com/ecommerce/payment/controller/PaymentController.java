package com.ecommerce.payment.controller;

import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.service.PaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Contract matches order-service's Feign client (PaymentServiceClient):
 *   POST /payments/charge            -> { id, status }
 *   GET  /payments/{paymentId}/status -> "SUCCESS" | "FAILED" | "PENDING" | "UNKNOWN"
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    public ChargeResponse charge(@RequestHeader("X-User-Id") String userId,
                                 @RequestBody ChargeRequest request) {
        Payment payment = paymentService.charge(userId, request.getOrderId(), request.getAmount());
        return new ChargeResponse(payment.getId().toString(), payment.getStatus().name());
    }

    @GetMapping("/{paymentId}/status")
    public PaymentStatus status(@PathVariable String paymentId) {
        return paymentService.status(paymentId);
    }

    @Data
    public static class ChargeRequest {
        private String orderId;
        private BigDecimal amount;
    }

    public record ChargeResponse(String id, String status) { }
}
