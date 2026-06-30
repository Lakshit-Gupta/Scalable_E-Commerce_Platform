package com.ecommerce.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3-compatible object storage clients (v0.0.13). Works against MinIO locally and Cloudflare R2 in
 * prod — only {@code storage.*} config differs. Path-style access is required by MinIO/R2.
 */
@Configuration
public class S3Config {

    @Bean
    S3Client s3Client(@Value("${storage.endpoint}") String endpoint,
                      @Value("${storage.region}") String region,
                      @Value("${storage.access-key}") String accessKey,
                      @Value("${storage.secret-key}") String secretKey) {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            .build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${storage.endpoint}") String endpoint,
                            @Value("${storage.region}") String region,
                            @Value("${storage.access-key}") String accessKey,
                            @Value("${storage.secret-key}") String secretKey) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }
}
