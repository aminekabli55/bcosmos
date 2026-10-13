package com.bcosmos.backend.ai.gateway;

import java.util.List;

/**
 * What an adapter has to send: already redacted, already resolved to one model.
 *
 * @param messages        the prompt, in order; system messages carry the instructions
 * @param jsonMode        whether the provider should be asked for JSON-only output
 * @param maxOutputTokens output cap
 * @param temperature     sampling temperature, or {@code null} for the provider default
 */
public record CompletionRequest(List<ChatMessage> messages, boolean jsonMode, int maxOutputTokens, Double temperature) {

    public CompletionRequest {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("at least one message is required");
        }
    }
}
