package com.ecommerce.recommendation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handler: retry 3 times with 1s delay, then publish to the dead-letter
 * topic (ecommerce.order-events.DLT) instead of silently dropping the event.
 */
@Configuration
@Slf4j
public class KafkaErrorHandlerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("[kafka-dlq] publishing to DLT after exhausted retries: topic={} offset={} error={}",
                    record.topic(), record.offset(), ex.getMessage());
                return new org.apache.kafka.common.TopicPartition(
                    record.topic() + ".DLT", record.partition());
            });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
