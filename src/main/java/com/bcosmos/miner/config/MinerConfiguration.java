package com.bcosmos.miner.config;

import com.bcosmos.core.events.KafkaTopics;
import java.time.Clock;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Miner infrastructure: Kafka error handling, DLQ routing, configuration (ADR 0026).
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(MinerProperties.class)
public class MinerConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            MinerProperties properties) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopics.deadLetterOf(record.topic()), record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.initialBackoffMs(), properties.backoffMultiplier());
        backOff.setMaxElapsedTime(properties.initialBackoffMs() * (long) Math.pow(properties.backoffMultiplier(), properties.maxRetryAttempts()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Do not retry poison messages with unallowed host, bad URL or unsupported schema (ADR 0026)
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
