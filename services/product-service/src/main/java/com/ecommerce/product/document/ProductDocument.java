package com.ecommerce.product.document;

import com.ecommerce.product.model.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;

@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "english")
    private String name;           // full-text searchable

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;      // exact match filter

    @Field(type = FieldType.Keyword)
    private String brand;         // exact match filter

    @Field(type = FieldType.Double)
    private BigDecimal price;     // range filter

    @Field(type = FieldType.Integer)
    private int stockQuantity;

    @Field(type = FieldType.Float)
    private float averageRating;

    public static ProductDocument from(Product product) {
        return ProductDocument.builder()
            .id(product.getId().toString())
            .name(product.getName())
            .description(product.getDescription())
            .category(product.getCategory())
            .brand(product.getBrand())
            .price(product.getPrice())
            .stockQuantity(product.getStockQuantity())
            .averageRating(product.getAverageRating())
            .build();
    }
}
