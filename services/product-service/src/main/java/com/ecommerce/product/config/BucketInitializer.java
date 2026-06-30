package com.ecommerce.product.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Ensures the media bucket exists at startup (v0.0.13). Retries a few times since MinIO may still be
 * coming up (compose dependency is service_started, not healthy).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BucketInitializer {

    private final S3Client s3Client;

    @Value("${storage.bucket}")
    private String bucket;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                s3Client.headBucket(b -> b.bucket(bucket));
                log.info("[storage] bucket '{}' present", bucket);
                return;
            } catch (NoSuchBucketException notFound) {
                s3Client.createBucket(b -> b.bucket(bucket));
                log.info("[storage] created bucket '{}'", bucket);
                return;
            } catch (Exception e) {
                log.warn("[storage] bucket check attempt {}/10 failed: {}", attempt, e.toString());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("[storage] giving up ensuring bucket '{}' — uploads will fail until storage is reachable", bucket);
    }
}
