package com.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// We use RedisTemplate + cache (not Spring Data Redis repos) and imperative ES (not reactive),
// so disable those repo auto-scans to remove cross-store "could not identify store" warnings.
@SpringBootApplication(exclude = {
    RedisRepositoriesAutoConfiguration.class,
    ReactiveElasticsearchRepositoriesAutoConfiguration.class
})
@EnableCaching
@EnableJpaRepositories(basePackages = "com.ecommerce.product.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.ecommerce.product.repository.search")
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
