package com.bcosmos.core.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The envelope every Kafka message carries (spec v2 section 5.2, ADR 0010).
 *
 * <p>An event is a fact with a version, not a bare payload: {@code eventId} makes a duplicate delivery traceable,
 * {@code schemaVersion} says which shape the payload has so a consumer can refuse one it does not understand, and
 * {@code correlationId} ties the messages of one request together across modules.</p>
 *
 * @param <T> the payload type; it is the only part a consumer has to know about
 */
public record EventEnvelope<T>(
        String eventId,
        EventType eventType,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        Long repositoryId,
        T payload) {

    /** Version of every payload shape currently on the wire. Bump it when a payload changes incompatibly. */
    public static final int SCHEMA_VERSION = 1;

    public EventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive, was " + schemaVersion);
        }
    }

    /**
     * A new event of {@code eventType} with a fresh id and timestamp, carrying the current schema version.
     *
     * @param correlationId ties the messages of one request together; a new random id when {@code null}
     */
    public static <T> EventEnvelope<T> of(EventType eventType, Long repositoryId, String correlationId, T payload) {
        String id = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, SCHEMA_VERSION, Instant.now(), id,
                repositoryId, payload);
    }

    /** The same event with a different correlation id, used when a consumer passes the request's id onward. */
    public EventEnvelope<T> withCorrelation(String newCorrelationId) {
        return new EventEnvelope<>(eventId, eventType, schemaVersion, occurredAt, newCorrelationId, repositoryId,
                payload);
    }

    /** Whether a consumer can read this shape. Named with a verb phrase so Jackson does not serialize it as a bean property. */
    public boolean understandsSchema() {
        return schemaVersion == SCHEMA_VERSION;
    }
}
