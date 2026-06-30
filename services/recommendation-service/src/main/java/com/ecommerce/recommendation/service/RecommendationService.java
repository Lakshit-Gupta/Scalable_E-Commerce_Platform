package com.ecommerce.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Phase-1 recommendations (v0.0.12) backed by Redis sorted sets:
 *   reco:popularity            ZSET(productId -> purchase count)        -> trending
 *   reco:cobought:{productId}  ZSET(otherProductId -> co-purchase count) -> "frequently bought together"
 * Aggregates are updated from the Kafka order-event stream. Events are at-least-once, so counts may
 * over-count slightly on rare redelivery — acceptable for trending/co-purchase signals.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    static final String POPULARITY_KEY = "reco:popularity";
    static final String COBOUGHT_PREFIX = "reco:cobought:";

    private final StringRedisTemplate redis;

    public void recordPurchase(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        ZSetOperations<String, String> zset = redis.opsForZSet();
        List<String> distinct = productIds.stream().distinct().toList();

        for (String product : distinct) {
            zset.incrementScore(POPULARITY_KEY, product, 1);
        }
        // every unordered pair in the same order co-occurs once
        for (int i = 0; i < distinct.size(); i++) {
            for (int j = i + 1; j < distinct.size(); j++) {
                String a = distinct.get(i);
                String b = distinct.get(j);
                zset.incrementScore(COBOUGHT_PREFIX + a, b, 1);
                zset.incrementScore(COBOUGHT_PREFIX + b, a, 1);
            }
        }
    }

    public List<Scored> trending(int limit) {
        return toScored(redis.opsForZSet().reverseRangeWithScores(POPULARITY_KEY, 0, boundary(limit)));
    }

    public List<Scored> frequentlyBoughtTogether(String productId, int limit) {
        return toScored(redis.opsForZSet().reverseRangeWithScores(COBOUGHT_PREFIX + productId, 0, boundary(limit)));
    }

    private long boundary(int limit) {
        return Math.max(0, limit - 1L);
    }

    private List<Scored> toScored(Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
            .map(t -> new Scored(t.getValue(), t.getScore() == null ? 0L : t.getScore().longValue()))
            .toList();
    }

    public record Scored(String productId, long count) {
    }
}
