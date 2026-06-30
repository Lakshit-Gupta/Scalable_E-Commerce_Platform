package com.ecommerce.notification.analytics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Backend product-analytics capture via PostHog (v0.0.17). Uses Spring {@link RestClient} (no extra
 * dependency) to POST to the PostHog capture API. Graceful no-op when {@code POSTHOG_API_KEY} is
 * unset, so dev/CI never break and order processing is never blocked by analytics.
 */
@Component
@Slf4j
public class PostHogAnalytics {

    private final RestClient restClient;
    private final String apiKey;
    private final String host;

    public PostHogAnalytics(@Value("${POSTHOG_API_KEY:}") String apiKey,
                            @Value("${POSTHOG_HOST:https://us.i.posthog.com}") String host,
                            RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.host = host;
        this.restClient = restClientBuilder.build();
    }

    public void capture(String distinctId, String event, Map<String, Object> properties) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("[posthog] no POSTHOG_API_KEY set — skipping capture of '{}'", event);
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("api_key", apiKey);
            body.put("event", event);
            body.put("distinct_id", (distinctId == null || distinctId.isBlank()) ? "anonymous" : distinctId);
            body.put("properties", properties == null ? Map.of() : properties);
            body.put("timestamp", Instant.now().toString());

            restClient.post()
                .uri(host + "/capture/")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.info("[posthog] captured '{}' for {}", event, distinctId);
        } catch (Exception e) {
            log.warn("[posthog] capture of '{}' failed: {}", event, e.getMessage());
        }
    }
}
