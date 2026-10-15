package com.bcosmos.backend.events;

import com.bcosmos.core.events.EventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes the envelope as a JSON string.
 *
 * <p>The value is serialized here instead of handing the record to a JSON serializer with a type header: the three
 * modules are separate builds, and a message whose type header names a class another module cannot load is worse
 * than a plain JSON document both sides can parse into their own copy of the same record.</p>
 */
@Component
@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void publish(String topic, String key, EventEnvelope<?> event) {
        String value;
        try {
            value = json.writeValueAsString(event);
        } catch (RuntimeException e) {
            // A payload that cannot be serialized is a bug in the event, not a transient failure: fail loudly.
            throw new PipelineException("event " + event.eventType() + " could not be serialized for " + topic, e);
        }
        kafka.send(topic, key, value);
    }
}
