package com.ecommerce.product.controller;

import com.ecommerce.product.document.ProductDocument;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    // ProductNotFoundException (404) renders as RFC-7807 via common's GlobalExceptionHandler.

    /** Paginated catalog listing from Postgres. */
    @GetMapping
    public List<Product> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return productService.listAll(page, size);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable UUID id) {
        return productService.getProduct(id);
    }

    /** Rebuild the Elasticsearch index from Postgres (admin/demo helper; requires a bearer token). */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        return Map.of("reindexed", productService.reindexAll());
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductService.CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable UUID id, @RequestBody ProductService.UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    /** Full-text + faceted search via Elasticsearch. */
    @GetMapping("/search")
    public List<ProductDocument> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productSearchService.search(q, category, minPrice, maxPrice, page, size)
            .stream()
            .map(SearchHit::getContent)
            .toList();
    }

    /** Content-based "more like this" recommendations (Elasticsearch MLT) for a given product. */
    @GetMapping("/{id}/similar")
    public List<ProductDocument> similar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int size) {
        return productSearchService.moreLikeThis(id.toString(), size)
            .stream()
            .map(SearchHit::getContent)
            .filter(p -> !id.toString().equals(p.getId()))   // exclude the seed product itself
            .toList();
    }
}
