package com.ecommerce.order.service;

import com.ecommerce.common.error.BadRequestException;
import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.error.ServiceUnavailableException;
import com.ecommerce.grpc.payment.ChargeRequest;
import com.ecommerce.grpc.payment.ChargeResponse;
import com.ecommerce.grpc.payment.PaymentServiceGrpc;
import com.ecommerce.grpc.product.OrderRef;
import com.ecommerce.grpc.product.ProductServiceGrpc;
import com.ecommerce.grpc.product.ReserveLine;
import com.ecommerce.grpc.product.ReserveRequest;
import com.ecommerce.grpc.product.ReserveResponse;
import com.ecommerce.order.model.IdempotencyKey;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.publisher.OrderEventPublisher;
import com.ecommerce.order.repository.IdempotencyKeyRepository;
import com.ecommerce.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderConfirmationService orderConfirmationService;
    private final MeterRegistry meterRegistry;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    // net.devh injects the blocking stub; address resolved via Eureka (discovery:///PAYMENT-SERVICE)
    @GrpcClient("payment")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    @GrpcClient("product")
    private ProductServiceGrpc.ProductServiceBlockingStub productStub;

    private Counter ordersPlaced;
    private Counter ordersFailed;
    private Counter ordersReserveRejected;
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
        ordersReserveRejected = Counter.builder("orders.reserved.rejected.total")
            .description("Orders rejected for insufficient stock")
            .register(meterRegistry);
        orderProcessingTime = Timer.builder("orders.processing.duration")
            .description("Time to process an order")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    public Order placeOrder(CreateOrderRequest request, String userId) {
        return placeOrder(request, userId, null);
    }

    // idempotencyKey (optional, from the Idempotency-Key header): a retry with the same key returns the
    // first order instead of placing/charging again. null/blank -> no dedupe (behaves as before).
    public Order placeOrder(CreateOrderRequest request, String userId, String idempotencyKey) {
        boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();
        // Namespace the key by the (trusted, gateway-injected) userId so one user's key can never
        // collide with — and expose — another user's order.
        String scopedKey = idempotent ? userId + "|" + idempotencyKey : null;
        if (idempotent) {
            Order existing = findExistingByKey(scopedKey);
            if (existing != null) {
                return existing;   // replay — no second order, no second charge
            }
        }
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Order total must be positive");
        }
        if (request.getItems() != null) {
            boolean hasZeroPrice = request.getItems().stream()
                .anyMatch(i -> i.getPrice() == null || i.getPrice().compareTo(BigDecimal.ZERO) <= 0);
            if (hasZeroPrice) {
                throw new BadRequestException("All order items must have a positive price");
            }
        }
        return orderProcessingTime.record(() -> {
            // Persist a PENDING shell first — order survives even if payment fails
            Order order = Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .totalAmount(request.getTotalAmount())
                .status(Order.OrderStatus.PENDING)
                .items(toItems(request.getItems()))
                .build();
            // Persist PENDING order + idempotency key atomically. A concurrent request with the same
            // key loses the unique-constraint race here -> return the winner's order.
            try {
                orderConfirmationService.createPending(order, scopedKey);
            } catch (DataIntegrityViolationException dup) {
                Order winner = findExistingByKey(scopedKey);
                if (winner != null) {
                    return winner;
                }
                throw new ConflictException("Duplicate request already in progress");
            }

            // Saga step 1: reserve stock atomically (product-service owns it). Business rejection
            // (no stock) -> 409 + CANCELLED, no payment attempted. product-service down -> 503 (breaker).
            reserveStock(order);

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
                // Saga step 3: payment settled -> confirm the reservation (stock stays decremented).
                confirmReservation(order.getId());
                // Atomically persist CONFIRMED + stage the Kafka domain event (transactional outbox).
                orderConfirmationService.confirmAndStage(order);

                // RabbitMQ command for transactional email (work queue, distinct from the Kafka stream).
                orderEventPublisher.publishOrderPlaced(order);
                ordersPlaced.increment();
                return order;
            } catch (RuntimeException e) {
                ordersFailed.increment();
                // Compensation: free the stock we reserved. Idempotent + best-effort; the product-service
                // TTL sweeper is the backstop if this call also fails. Order stays PENDING (existing
                // reconciliation semantics) — never oversells because stock is released.
                releaseReservation(order.getId());
                throw e;
            }
        });
    }

    // Resolve the order an idempotency key already created. Key->orderId is written in the same tx as
    // the order, so a present key always has its order.
    private Order findExistingByKey(String idempotencyKey) {
        return idempotencyKeyRepository.findById(idempotencyKey)
            .map(IdempotencyKey::getOrderId)
            .flatMap(orderRepository::findWithItemsById)   // eager items — replay serializes a detached order
            .orElse(null);
    }

    private void reserveStock(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;   // amount-only order (no line items) — nothing to reserve
        }
        ReserveRequest req = ReserveRequest.newBuilder()
            .setOrderId(order.getId().toString())
            .addAllLines(order.getItems().stream()
                .map(i -> ReserveLine.newBuilder()
                    .setProductId(i.getProductId())
                    .setQuantity(i.getQuantity())
                    .build())
                .toList())
            .build();

        ReserveResponse res = circuitBreakerFactory.create("product").run(
            () -> productStub.reserve(req),
            throwable -> {
                throw new ServiceUnavailableException(
                    "Inventory service unavailable. Order saved, not reserved.", throwable);
            });

        if (!res.getOk()) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);
            ordersReserveRejected.increment();
            throw new ConflictException("Insufficient stock: " + res.getReason());
        }
    }

    private void confirmReservation(UUID orderId) {
        // ponytail: if product-service dies in the window between a successful charge and this call, the
        // order is CONFIRMED+paid but its reservation stays PENDING and the TTL sweeper will release it
        // (paid, stock returned). Low probability. Upgrade path: retry/reconcile confirm for CONFIRMED
        // orders whose reservations aren't CONFIRMED. Not built until it actually bites.
        productStub.confirm(OrderRef.newBuilder().setOrderId(orderId.toString()).build());
    }

    private void releaseReservation(UUID orderId) {
        try {
            productStub.release(OrderRef.newBuilder().setOrderId(orderId.toString()).build());
        } catch (RuntimeException ignored) {
            // best-effort — sweeper releases on TTL if product-service is unreachable now
        }
    }

    public Order getOrder(UUID id) {
        return orderRepository.findWithItemsById(id)   // eager items for serialization
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
