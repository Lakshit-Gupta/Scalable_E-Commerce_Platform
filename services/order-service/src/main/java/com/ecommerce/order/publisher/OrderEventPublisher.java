package com.ecommerce.order.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.order.model.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publishOrderPlaced(Order order) {
        OrderPlacedEvent event = OrderPlacedEvent.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .items(order.getItems())
            .totalAmount(order.getTotalAmount())
            .placedAt(Instant.now())
            .build();

        rabbitTemplate.convertAndSend(
            "order.exchange",           // exchange
            "order.placed",             // routing key
            event,                      // payload (auto-serialized to JSON)
            message -> {
                // Message properties
                message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
                message.getMessageProperties().setTimestamp(new Date());
                return message;
            }
        );
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPlacedEvent {
        private UUID orderId;
        private String userId;
        private List<Order.OrderItem> items;
        private BigDecimal totalAmount;
        private Instant placedAt;
        private int retryCount;
    }
}
