package com.bcosmos.backend.persistence;

/**
 * What a registry row is for.
 *
 * <p>It is not decoration: asking a chat model for embeddings, or making an embedding model the default for chat,
 * are both silent failures without it. The registry refuses the wrong capability with a message that names the
 * model, instead of sending a request that a provider will reject (or worse, accept and return nonsense for).</p>
 */
public enum LlmModelCapability {

    /** Free text and schema-validated answers: {@code chat} and {@code structured}. */
    CHAT,

    /** Embedding vectors: {@code embed}, and the only models allowed into {@code code_chunk.embedding}. */
    EMBEDDING
}
