package com.ecommerce.product.repository.jpa;

import com.ecommerce.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Spring reads method name at startup and generates SQL automatically
    List<Product> findByCategoryAndPriceBetweenOrderByPriceAsc(
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice
    );

    // For complex queries, write JPQL explicitly
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0 AND p.category = :category")
    Page<Product> findAvailableByCategory(
        @Param("category") String category,
        Pageable pageable
    );
}
