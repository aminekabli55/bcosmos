package com.bcosmos.backend.config;

import com.bcosmos.core.events.KafkaTopics;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the pipeline's topics, including one dead-letter topic per working topic (spec v2 section 5.1).
 *
 * <p>Created from the application rather than from a broker script so a fresh deployment is one `docker compose`
 * command away from a working pipeline, and so the partition count is the same everywhere.</p>
 */
@Configuration
@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(PipelineProperties.class)
public class KafkaTopicsConfiguration {

    /** Every topic the pipeline uses, in one list: a new topic is added here and nowhere else. */
    static final List<String> PIPELINE_TOPICS = List.of(
            KafkaTopics.ANALYSIS_REQUESTED,
            KafkaTopics.MINE_REQUESTED,
            KafkaTopics.COMMIT_DISCOVERED,
            KafkaTopics.FINDINGS);

    @Bean
    List<NewTopic> pipelineTopics(PipelineProperties properties) {
        return PIPELINE_TOPICS.stream()
                .flatMap(topic -> java.util.stream.Stream.of(topic, KafkaTopics.deadLetterOf(topic)))
                .map(topic -> TopicBuilder.name(topic)
                        .partitions(properties.partitions())
                        .replicas(properties.replicas())
                        .build())
                .toList();
    }
}
