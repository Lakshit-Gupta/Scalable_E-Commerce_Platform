package com.ecommerce.notification.service;

import com.ecommerce.notification.consumer.OrderEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub email sender — logs instead of calling SendGrid/Twilio.
 * Swap for a real provider client in a later version.
 */
@Service
@Slf4j
public class LoggingEmailService implements OrderEventConsumer.EmailService {

    @Override
    public void sendOrderConfirmation(String userId, UUID orderId) {
        log.info("[EMAIL STUB] Order confirmation -> user={} order={}", userId, orderId);
    }
}
