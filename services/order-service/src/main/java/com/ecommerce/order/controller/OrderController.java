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

    @PostMapping
    public ResponseEntity<Order> place(@RequestHeader("X-User-Id") String userId,
                                       @RequestBody OrderService.CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request, userId));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }
}
