package com.ecommerce.cart.controller;

import com.ecommerce.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // X-User-Id is injected by the gateway after JWT validation.

    @GetMapping
    public Map<String, Integer> getCart(@RequestHeader("X-User-Id") String userId) {
        return cartService.getCart(userId);
    }

    @PostMapping("/items")
    public ResponseEntity<Void> addItem(@RequestHeader("X-User-Id") String userId,
                                        @RequestBody CartService.CartItem item) {
        cartService.addItem(userId, item);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@RequestHeader("X-User-Id") String userId,
                                           @PathVariable String productId) {
        cartService.removeItem(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestHeader("X-User-Id") String userId) {
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
