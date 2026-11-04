package com.bcosmos.backend.ai.gateway;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/**
 * A call that must come back as JSON matching a schema (spec v2 section 7.1).
 *
 * <p>The same schema is sent in the prompt and used to validate the answer; a failure is retried once with the
 * validation error attached.</p>
 *
 * @param modelId         registry id, or {@code null} to use the default model
 * @param messages        the prompt; the schema is appended by the gateway, so callers never format it themselves
 * @param responseType    what a valid answer binds to
 * @param jsonSchema      the schema, as a parsed JSON document
 * @param promptVersion   versioned prompt name recorded with the usage row
 * @param maxOutputTokens output cap, or {@code null} for the configured default
 * @param <T>             the bound type
 */
public record StructuredRequest<T>(
        Long modelId,
        List<ChatMessage> messages,
        Class<T> responseType,
        JsonNode jsonSchema,
        String promptVersion,
        Integer maxOutputTokens) {

    public StructuredRequest {
        messages = List.copyOf(messages);
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(jsonSchema, "jsonSchema");
    }

    public static <T> StructuredRequest<T> of(Long modelId, List<ChatMessage> messages, Class<T> responseType,
            JsonNode jsonSchema, String promptVersion) {
        return new StructuredRequest<>(modelId, messages, responseType, jsonSchema, promptVersion, null);
    }
}
