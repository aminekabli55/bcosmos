package com.bcosmos.backend.events;

import com.bcosmos.core.events.EventEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The publisher a deployment without Kafka gets.
 *
 * <p>It refuses instead of dropping: an endpoint that asks the pipeline to do something while the pipeline is off
 * answers with a clear 409 ("turn bcosmos.kafka.enabled on") rather than a 200 that quietly did nothing, which is
 * the failure mode a corpus ingest would otherwise have.</p>
 */
@Component
@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEventPublisher implements EventPublisher {

    @Override
    public void publish(String topic, String key, EventEnvelope<?> event) {
        throw new PipelineException("the Kafka pipeline is off: set bcosmos.kafka.enabled=true to publish to "
                + topic);
    }
}
