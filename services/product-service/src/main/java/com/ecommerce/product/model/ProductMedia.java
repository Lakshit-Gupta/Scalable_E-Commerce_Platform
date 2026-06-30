package com.ecommerce.product.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Media object stored for a product (v0.0.13). Holds only the object key + metadata — the bytes live
 * in S3-compatible storage (MinIO/R2), never in the service or DB.
 */
@Entity
@Table(name = "product_media")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMedia {

    @Id
    private UUID id;

    private String productId;

    private String objectKey;

    private String contentType;

    private Instant createdAt;
}
