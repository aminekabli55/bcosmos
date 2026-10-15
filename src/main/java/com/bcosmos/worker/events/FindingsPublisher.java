package com.bcosmos.worker.events;

import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.FindingsPayload;
import com.bcosmos.core.events.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes analysis findings to the Kafka topic {@code analysis.findings.v1} (spec v2 section 5.2).
 */
@Component
public class FindingsPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public FindingsPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    public void publish(EventEnvelope<FindingsPayload> envelope) {
        String key = String.valueOf(envelope.repositoryId());
        String value;
        try {
            value = json.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize findings event envelope", e);
        }
        kafka.send(KafkaTopics.FINDINGS, key, value);
    }
}
