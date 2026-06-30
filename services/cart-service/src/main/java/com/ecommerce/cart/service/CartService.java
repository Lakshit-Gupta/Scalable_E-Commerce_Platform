package com.ecommerce.cart.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final Duration CART_TTL = Duration.ofDays(7);

    private String cartKey(String userId) {
        return "cart:" + userId;
    }

    public void addItem(String userId, CartItem item) {
        // Redis HASH: field=productId, value=quantity
        // HSET cart:user123 product:456 2
        redisTemplate.opsForHash().put(
            cartKey(userId),
            "product:" + item.getProductId(),
            item.getQuantity()
        );
        // Reset TTL on activity
        redisTemplate.expire(cartKey(userId), CART_TTL);
    }

    public Map<String, Integer> getCart(String userId) {
        // HGETALL cart:user123
        Map<Object, Object> entries = redisTemplate
            .opsForHash()
            .entries(cartKey(userId));
            
        return entries.entrySet().stream()
            .collect(Collectors.toMap(
                e -> (String) e.getKey(),
                e -> (Integer) e.getValue()
            ));
    }

    public void removeItem(String userId, String productId) {
        // HDEL cart:user123 product:456
        redisTemplate.opsForHash().delete(
            cartKey(userId),
            "product:" + productId
        );
    }

    public void clearCart(String userId) {
        // DEL cart:user123 (called after order placed)
        redisTemplate.delete(cartKey(userId));
    }

    @Data
    public static class CartItem {
        private String productId;
        private int quantity;
    }
}
