package com.ecommerce.product.service;

import com.ecommerce.product.model.ProductMedia;
import com.ecommerce.product.repository.jpa.ProductMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Product media via presigned URLs (v0.0.13): clients upload/download directly to object storage, so
 * file bytes never pass through this service. We only persist the object key + metadata and mint
 * short-lived presigned URLs (PUT to upload, GET to view).
 */
@Service
@RequiredArgsConstructor
public class ProductMediaService {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_TTL = Duration.ofHours(1);

    private final S3Presigner presigner;
    private final ProductMediaRepository repository;

    @Value("${storage.bucket}")
    private String bucket;

    public PresignedUpload presignUpload(String productId, String filename, String contentType) {
        String key = "products/%s/%s-%s".formatted(productId, UUID.randomUUID(), sanitize(filename));
        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        PutObjectRequest put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(ct).build();
        String url = presigner.presignPutObject(b -> b.signatureDuration(UPLOAD_TTL).putObjectRequest(put))
            .url().toString();

        repository.save(ProductMedia.builder()
            .id(UUID.randomUUID())
            .productId(productId)
            .objectKey(key)
            .contentType(ct)
            .createdAt(Instant.now())
            .build());

        return new PresignedUpload(key, url, UPLOAD_TTL.toSeconds());
    }

    public List<MediaItem> list(String productId) {
        return repository.findByProductId(productId).stream()
            .map(m -> new MediaItem(m.getObjectKey(), presignGet(m.getObjectKey()), m.getContentType()))
            .toList();
    }

    private String presignGet(String key) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(b -> b.signatureDuration(DOWNLOAD_TTL).getObjectRequest(get))
            .url().toString();
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record PresignedUpload(String key, String uploadUrl, long expiresInSeconds) {
    }

    public record MediaItem(String key, String url, String contentType) {
    }
}
