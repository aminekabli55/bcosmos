package com.bcosmos.backend.ai.gateway;

import java.util.List;
import java.util.Objects;

/**
 * Embedding vectors, one per requested text, in the same order.
 *
 * <p>The vector size belongs to the embedding model, which is why the model id and name travel with the result and
 * are stored next to any vector this project keeps (spec v2 section 7.2).</p>
 *
 * @param vectors     one vector per input text, in order
 * @param modelId     registry id of the model that produced them
 * @param modelName   the provider's model name
 * @param usage       tokens, latency and estimated cost
 */
public record EmbedResult(List<float[]> vectors, long modelId, String modelName, LlmUsage usage) {

    public EmbedResult {
        vectors = List.copyOf(vectors);
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(usage, "usage");
    }

    /** Vector length, or 0 for an empty result (which the gateway never returns). */
    public int dimensions() {
        return vectors.isEmpty() ? 0 : vectors.getFirst().length;
    }
}
