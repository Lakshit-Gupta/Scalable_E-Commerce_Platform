package com.ecommerce.order.service;

import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.error.ServiceUnavailableException;
import com.ecommerce.grpc.payment.ChargeRequest;
import com.ecommerce.grpc.payment.ChargeResponse;
import com.ecommerce.grpc.payment.PaymentServiceGrpc;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.publisher.OrderEventPublisher;
import com.ecommerce.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderConfirmationService orderConfirmationService;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    // net.devh injects the blocking stub; address resolved via Eureka (discovery:///PAYMENT-SERVICE)
    @GrpcClient("payment")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    private Counter ordersPlaced;
    private Counter ordersFailed;
    private Timer orderProcessingTime;

    @PostConstruct
    void initMetrics() {
        ordersPlaced = Counter.builder("orders.placed.total")
            .description("Total orders successfully placed")
            .tag("service", "order-service")
            .register(meterRegistry);
        ordersFailed = Counter.builder("orders.failed.total")
            .description("Total failed order attempts")
            .register(meterRegistry);
        orderProcessingTime = Timer.builder("orders.processing.duration")
            .description("Time to process an order")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    public Order placeOrder(CreateOrderRequest request, String userId) {
        return orderProcessingTime.record(() -> {
            // Persist a PENDING shell first — order survives even if payment fails
            Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalAmount(request.getTotalAmount())
                .status(Order.OrderStatus.PENDING)
                .items(toItems(request.getItems()))
                .build();
            orderRepository.save(order);

            try {
                ChargeRequest chargeRequest = ChargeRequest.newBuilder()
                    .setOrderId(order.getId().toString())
                    .setUserId(userId)
                    .setAmount(order.getTotalAmount().toPlainString())
                    .build();

                // gRPC call wrapped by the circuit breaker; open circuit / failure -> fallback throws 503
                ChargeResponse payment = circuitBreakerFactory.create("payment").run(
                    () -> paymentStub.charge(chargeRequest),
                    throwable -> {
                        throw new ServiceUnavailableException(
                            "Payment service unavailable. Order saved, payment pending.", throwable);
                    });

                order.setPaymentId(payment.getPaymentId());
                // Atomically persist CONFIRMED + stage the Kafka domain event (transactional outbox).
                orderConfirmationService.confirmAndStage(order);

                // RabbitMQ command for transactional email (work queue, distinct from the Kafka stream).
                orderEventPublisher.publishOrderPlaced(order);
                ordersPlaced.increment();
                return order;
            } catch (RuntimeException e) {
                ordersFailed.increment();
                throw e;   // order remains PENDING for later reconciliation
            }
        });
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private static java.util.List<Order.OrderItem> toItems(java.util.List<OrderItemRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return java.util.List.of();
        }
        return requested.stream()
            .map(r -> new Order.OrderItem(UUID.randomUUID(), r.getProductId(), r.getQuantity(), r.getPrice()))
            .toList();
    }

    @Data
    public static class CreateOrderRequest {
        private BigDecimal totalAmount;
        private java.util.List<OrderItemRequest> items;   // optional line items (productId, quantity, price)
    }

    @Data
    public static class OrderItemRequest {
        private String productId;
        private int quantity;
        private BigDecimal price;
    }

    /** 404 via common's RFC-7807 handler. */
    public static class OrderNotFoundException extends ResourceNotFoundException {
        public OrderNotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
