package com.ecommerce.recommendation.controller;

import com.ecommerce.recommendation.service.RecommendationService;
import com.ecommerce.recommendation.service.RecommendationService.Scored;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read API for phase-1 recommendations (v0.0.12). Served through the gateway at
 * {@code /api/recommendations/**}.
 */
@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** Top products by purchase count (trending). */
    @GetMapping("/trending")
    public List<Scored> trending(@RequestParam(defaultValue = "10") int limit) {
        return recommendationService.trending(limit);
    }

    /** Products most often bought in the same order as {@code productId}. */
    @GetMapping("/frequently-bought-together/{productId}")
    public List<Scored> frequentlyBoughtTogether(@PathVariable String productId,
                                                 @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.frequentlyBoughtTogether(productId, limit);
    }

    /**
     * Phase 2: personalized recommendations for a user based on their purchase history.
     * Uses item-based collaborative filtering over co-purchase aggregates.
     * Falls back to trending for cold-start (no history).
     */
    @GetMapping("/for-you/{userId}")
    public List<Scored> personalizedFor(@PathVariable String userId,
                                        @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.personalizedFor(userId, limit);
    }
}
