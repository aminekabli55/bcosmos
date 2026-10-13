package com.bcosmos.backend.ai.gateway;

import java.util.List;

/**
 * A free-text call to one model.
 *
 * @param modelId         registry id, or {@code null} to use the model marked as default
 * @param messages        system and user messages, in order
 * @param promptVersion   the versioned prompt that produced {@code messages}, recorded with the usage row
 * @param maxOutputTokens output cap, or {@code null} for the configured default
 * @param temperature     sampling temperature, or {@code null} for the provider default
 */
public record ChatRequest(
        Long modelId,
        List<ChatMessage> messages,
        String promptVersion,
        Integer maxOutputTokens,
        Double temperature) {

    public ChatRequest {
        messages = List.copyOf(messages);
    }

    public static ChatRequest of(Long modelId, List<ChatMessage> messages, String promptVersion) {
        return new ChatRequest(modelId, messages, promptVersion, null, null);
    }
}
