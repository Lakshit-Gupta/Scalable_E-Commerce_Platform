package com.ecommerce.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange — all order events go here
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange");
    }

    // Queues — each consumer gets its own queue
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("notification.queue")
            .withArgument("x-dead-letter-exchange", "order.dlx")   // failed messages -> DLX
            .withArgument("x-message-ttl", 86400000)               // messages expire after 24h
            .build();
    }

    @Bean
    public Queue inventoryQueue() {
        return QueueBuilder.durable("inventory.queue")
            .withArgument("x-dead-letter-exchange", "order.dlx")
            .build();
    }

    // Bindings — which queue gets which events
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
            .bind(notificationQueue())
            .to(orderExchange())
            .with("order.*");   // matches order.placed, order.cancelled, etc.
    }

    @Bean
    public Binding inventoryBinding() {
        return BindingBuilder
            .bind(inventoryQueue())
            .to(orderExchange())
            .with("order.placed");   // only new orders, not cancellations
    }

    // Dead Letter Exchange — for failed messages
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("order.dlx");
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("order.dead-letter").build();
    }

    @Bean
    public Binding deadLetterBinding() {
        // order.dlx is a DirectExchange — needs an exact key (no wildcards).
        // Dead-lettered messages retain their original routing key (order.placed).
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("order.placed");
    }

    // JSON serialization for published events. Uses the Boot-configured ObjectMapper
    // (JSR-310 enabled) so Instant fields serialize correctly.
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
