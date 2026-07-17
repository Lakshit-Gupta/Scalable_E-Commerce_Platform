package com.ecommerce.product.service;

import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.product.document.ProductDocument;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.jpa.ProductRepository;
import com.ecommerce.product.repository.search.ProductSearchRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository esRepository;
    private final RedisTemplate<String, Product> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(30);

    // Spring Cache Abstraction approach
    @Cacheable(
        value = "products",           // cache name
        key = "#productId",           // cache key
        unless = "#result == null"    // don't cache nulls
    )
    public Product getProduct(UUID productId) {
        // Only called on cache MISS
        // Spring intercepts, checks Redis first
        return productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    /** Paginated catalog listing straight from Postgres (the source of truth). */
    public List<Product> listAll(int page, int size) {
        return productRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    /** Re-mirror every Postgres product into the Elasticsearch index. Returns the count reindexed. */
    public int reindexAll() {
        List<Product> all = productRepository.findAll();
        esRepository.saveAll(all.stream().map(ProductDocument::from).toList());
        return all.size();
    }

    /** Partial update: null fields are left unchanged. Re-indexes ES so search stays in sync. */
    @CacheEvict(value = "products", key = "#productId")
    public Product updateProduct(UUID productId, UpdateProductRequest req) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getCategory() != null) product.setCategory(req.getCategory());
        if (req.getBrand() != null) product.setBrand(req.getBrand());
        if (req.getPrice() != null) product.setPrice(req.getPrice());
        if (req.getStockQuantity() != null) product.setStockQuantity(req.getStockQuantity());
        product = productRepository.save(product);
        esRepository.save(ProductDocument.from(product)); // create indexes ES; update must too
        return product;
    }
    
    @CachePut(value = "products", key = "#result.id")
    @Transactional
    public Product createProduct(CreateProductRequest req) {
        // 1. Save to PostgreSQL (source of truth)
        Product product = productRepository.save(Product.builder()
            .id(UUID.randomUUID())
            .name(req.getName())
            .description(req.getDescription())
            .category(req.getCategory())
            .brand(req.getBrand())
            .price(req.getPrice())
            .stockQuantity(req.getStockQuantity())
            .build());
        
        // 2. Index in Elasticsearch (search layer)
        esRepository.save(ProductDocument.from(product));
        
        // 3. Publish event for other services
        rabbitTemplate.convertAndSend(
            "product.exchange",
            "product.created",
            product.getId().toString()
        );
        
        return product;
    }

    @Data
    public static class CreateProductRequest {
        private String name;
        private String description;
        private String category;
        private String brand;
        private BigDecimal price;
        private int stockQuantity;
    }

    @Data
    public static class UpdateProductRequest {
        private String name;
        private String description;
        private String category;
        private String brand;
        private BigDecimal price;
        private Integer stockQuantity;   // nullable: absent field = keep current (was silently dropped)
    }
}
