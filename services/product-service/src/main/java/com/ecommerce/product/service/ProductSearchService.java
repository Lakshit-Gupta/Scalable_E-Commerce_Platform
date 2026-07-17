package com.ecommerce.product.service;

import co.elastic.clients.json.JsonData;
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

    /**
     * Full-text + faceted catalog search. A {@code bool} query combines:
     *  - a scoring {@code multi_match} over name/description/category/brand (fuzzy, name boosted)
     *    — or {@code match_all} when no text query is supplied (browse the whole catalog);
     *  - a {@code term} filter on the exact category keyword (non-scoring);
     *  - a {@code range} filter on price (non-scoring).
     * Filters live in the bool {@code filter} clause so they narrow results without affecting relevance.
     */
    public SearchHits<ProductDocument> search(
        String query,
        String category,
        BigDecimal minPrice, BigDecimal maxPrice,
        int page, int size
    ) {
        boolean hasText = query != null && !query.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasPrice = minPrice != null || maxPrice != null;

        Query searchQuery = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> {
                if (hasText) {
                    b.must(m -> m.multiMatch(mm -> mm
                        .query(query)
                        .fields("name^3", "description", "category", "brand")
                        .fuzziness("AUTO")));
                } else {
                    b.must(m -> m.matchAll(ma -> ma));
                }
                if (hasCategory) {
                    b.filter(f -> f.term(t -> t.field("category").value(category)));
                }
                if (hasPrice) {
                    b.filter(f -> f.range(r -> {
                        r.field("price");
                        if (minPrice != null) r.gte(JsonData.of(minPrice.doubleValue()));
                        if (maxPrice != null) r.lte(JsonData.of(maxPrice.doubleValue()));
                        return r;
                    }));
                }
                return b;
            }))
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
