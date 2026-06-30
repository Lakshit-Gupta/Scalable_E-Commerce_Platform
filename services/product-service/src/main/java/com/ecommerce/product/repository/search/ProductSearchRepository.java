package com.ecommerce.product.repository.search;

import com.ecommerce.product.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    // Spring generates the ES query from the method name
    List<ProductDocument> findByCategoryAndPriceBetween(
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice
    );
}
