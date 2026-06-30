package com.ecommerce.product.service;

import com.ecommerce.product.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchOperations esOps;

    public SearchHits<ProductDocument> search(
        String query, 
        String category,
        BigDecimal minPrice, BigDecimal maxPrice,
        int page, int size
    ) {
        // Build ES query programmatically using NativeQuery builder pattern
        Query searchQuery = NativeQuery.builder()
            .withPageable(PageRequest.of(page, size))
            .build();

        return esOps.search(searchQuery, ProductDocument.class);
    }

    /**
     * Content-based "more like this" recommendations (v0.0.19): an Elasticsearch More-Like-This query
     * seeded by an existing product document, scored over its text/keyword fields. Returns similar
     * products excluding the seed itself. min_term_freq / min_doc_freq are lowered to 1 so the
     * feature still works on small dev catalogs (ES defaults would filter everything out).
     */
    public SearchHits<ProductDocument> moreLikeThis(String productId, int size) {
        Query mltQuery = NativeQuery.builder()
            .withQuery(q -> q
                .moreLikeThis(mlt -> mlt
                    .fields("name", "description", "category", "brand")
                    .like(like -> like.document(d -> d.index("products").id(productId)))
                    .minTermFreq(1)
                    .minDocFreq(1)
                    .maxQueryTerms(12)))
            .withPageable(PageRequest.of(0, size))
            .build();

        return esOps.search(mltQuery, ProductDocument.class);
    }
}
