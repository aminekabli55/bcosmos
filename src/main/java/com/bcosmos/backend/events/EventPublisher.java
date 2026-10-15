package com.bcosmos.backend.events;

import com.bcosmos.core.events.EventEnvelope;

/**
 * Publishes one envelope to one topic.
 *
 * <p>A narrow port rather than a {@code KafkaTemplate} in every caller: the job dispatcher and the corpus service
 * state <em>what</em> happened, and this interface decides <em>how</em> it reaches the broker. It also makes both
 * testable without a broker, which matters because the pipeline is off by default.</p>
 */
public interface EventPublisher {

    /**
     * Sends {@code event} to {@code topic} with {@code key} as the partition key.
     *
     * @param key the partition key; per-repository ordering depends on every event of a repository sharing it
     */
    void publish(String topic, String key, EventEnvelope<?> event);
}
