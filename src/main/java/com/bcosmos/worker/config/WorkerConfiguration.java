package com.bcosmos.worker.config;

import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.pipeline.CommitAnalysisPipeline;
import com.bcosmos.core.quality.FindingNormalizer;
import com.bcosmos.core.quality.Fingerprint;
import com.bcosmos.core.typescript.TypeScriptAdapter;
import java.time.Clock;
import java.util.List;
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
 * Worker infrastructure: Kafka consumers, DLQ recovery, analysis pipeline beans (ADR 0026).
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(WorkerProperties.class)
public class WorkerConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CommitAnalysisPipeline commitAnalysisPipeline() {
        return new CommitAnalysisPipeline(List.of(new JavaAdapter(), new TypeScriptAdapter()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            WorkerProperties properties) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // DLQ recoverer routes failed records to their matching *.dlq.v1 topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(KafkaTopics.deadLetterOf(record.topic()), record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(
                properties.initialBackoffMs(), properties.backoffMultiplier());
        backOff.setMaxElapsedTime(properties.initialBackoffMs() * (long) Math.pow(properties.backoffMultiplier(), properties.maxRetryAttempts()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Do not retry poison messages with unhandled schema or unparseable payload (ADR 0026)
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
