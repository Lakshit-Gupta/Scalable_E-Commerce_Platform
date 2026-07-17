package com.ecommerce.notification.consumer;

import com.ecommerce.notification.service.TwilioSmsService;
import com.rabbitmq.client.Channel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final EmailService emailService;
    private final TwilioSmsService smsService;

    @RabbitListener(queues = "notification.queue")
    public void handleOrderPlaced(
        OrderPlacedEvent event,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
        Channel channel
    ) throws IOException {
        try {
            log.info("Processing order notification: {}", event.getOrderId());

            emailService.sendOrderConfirmation(
                event.getUserId(),
                event.getOrderId()
            );

            // SMS is best-effort and self-contained (never throws) — sent after the email so an SMS
            // failure can't block the email/ack path or trigger a requeue (v0.1.3).
            smsService.sendOrderConfirmation(event.getUserId(), event.getOrderId());

            // Manually acknowledge — message removed from queue
            channel.basicAck(deliveryTag, false);
            
        } catch (EmailException e) {
            log.error("Failed to send email for order: {}", event.getOrderId(), e);
            
            // Reject and requeue (up to 3 times)
            // After 3 failures → goes to DLQ automatically
            channel.basicNack(deliveryTag, false, shouldRequeue(event));
        }
    }
    
    private boolean shouldRequeue(OrderPlacedEvent event) {
        // Check retry count in message header
        // If > 3 attempts → don't requeue → goes to DLQ
        return event.getRetryCount() < 3;
    }

    @Data
    public static class OrderPlacedEvent {
        private UUID orderId;
        private String userId;
        private int retryCount;
    }

    public interface EmailService {
        void sendOrderConfirmation(String userId, UUID orderId);
    }

    public static class EmailException extends RuntimeException {
        public EmailException(String message) {
            super(message);
        }
    }
}
