package com.bcosmos.backend.ai.gateway;

import java.util.List;

/**
 * An embedding call (spec v2 section 7.2 uses this for code chunks in phase 3).
 *
 * @param modelId registry id of an embedding model, or {@code null} for the default model
 * @param texts   the texts to embed, in order
 */
public record EmbedRequest(Long modelId, List<String> texts) {

    public EmbedRequest {
        texts = List.copyOf(texts);
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("at least one text is required");
        }
    }
}
