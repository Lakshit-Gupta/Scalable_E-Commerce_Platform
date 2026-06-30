package com.ecommerce.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private UUID id;

    private String userId;

    private String paymentId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal totalAmount;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderItem> items;  // lazy = not loaded until accessed

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    @Entity
    @Table(name = "order_items")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        @Id
        private UUID id;
        private String productId;
        private int quantity;
        private BigDecimal price;
    }
}
