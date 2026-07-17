package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.model.UserPurchase;
import com.ecommerce.recommendation.repository.UserPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase-1 (v0.0.12): Redis ZSETs for trending + frequently-bought-together.
 * Phase-2: Item-based collaborative filtering using co-purchase aggregates.
 *   recordUserPurchase() persists userId→productId history in PostgreSQL.
 *   personalizedFor(userId) scores candidate products by summing co-purchase weights from
 *   the user's purchase history, then excludes already-bought items.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    static final String POPULARITY_KEY = "reco:popularity";
    static final String COBOUGHT_PREFIX = "reco:cobought:";
    private static final int COBOUGHT_CANDIDATES = 20;

    private final StringRedisTemplate redis;
    private final UserPurchaseRepository userPurchaseRepository;

    @Transactional
    public void recordPurchase(String userId, List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        ZSetOperations<String, String> zset = redis.opsForZSet();
        List<String> distinct = productIds.stream().distinct().toList();

        for (String product : distinct) {
            zset.incrementScore(POPULARITY_KEY, product, 1);
            if (userId != null && !userId.isBlank()) {
                userPurchaseRepository.save(UserPurchase.builder()
                    .userId(userId)
                    .productId(product)
                    .purchasedAt(Instant.now())
                    .build());
            }
        }
        for (int i = 0; i < distinct.size(); i++) {
            for (int j = i + 1; j < distinct.size(); j++) {
                String a = distinct.get(i);
                String b = distinct.get(j);
                zset.incrementScore(COBOUGHT_PREFIX + a, b, 1);
                zset.incrementScore(COBOUGHT_PREFIX + b, a, 1);
            }
        }
    }

    /** Legacy overload used by old callers without userId context. */
    public void recordPurchase(List<String> productIds) {
        recordPurchase(null, productIds);
    }

    /**
     * Item-based CF: aggregate co-purchase scores across the user's history.
     * For each product the user bought, fetch the top-N most co-bought items and add
     * their co-purchase score to the candidate set. Return ranked candidates not already bought.
     */
    @Transactional(readOnly = true)
    public List<Scored> personalizedFor(String userId, int limit) {
        List<String> bought = userPurchaseRepository.findDistinctProductIdsByUserId(userId);
        if (bought.isEmpty()) {
            return trending(limit);   // cold start: fall back to trending
        }
        Set<String> boughtSet = Set.copyOf(bought);
        Map<String, Double> scores = new HashMap<>();

        ZSetOperations<String, String> zset = redis.opsForZSet();
        for (String boughtProduct : bought) {
            Set<ZSetOperations.TypedTuple<String>> cobought =
                zset.reverseRangeWithScores(COBOUGHT_PREFIX + boughtProduct, 0, COBOUGHT_CANDIDATES - 1);
            if (cobought == null) continue;
            cobought.stream()
                .filter(t -> t.getValue() != null && !boughtSet.contains(t.getValue()))
                .forEach(t -> scores.merge(t.getValue(), t.getScore() == null ? 0 : t.getScore(), Double::sum));
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> new Scored(e.getKey(), e.getValue().longValue()))
            .toList();
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
