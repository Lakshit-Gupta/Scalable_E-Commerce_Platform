package com.ecommerce.order.controller;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Errors (404 not-found, 503 payment-down) render as RFC-7807 via common's GlobalExceptionHandler.

    // Idempotency-Key (optional): retrying with the same key returns the first order, no double charge.
    // ponytail: always 201 even on replay — status-code nicety isn't worth plumbing a replayed flag;
    // the guarantee that matters (no duplicate order/charge) holds either way.
    @PostMapping
    public ResponseEntity<Order> place(@RequestHeader("X-User-Id") String userId,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       @RequestBody OrderService.CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.placeOrder(request, userId, idempotencyKey));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }
}
