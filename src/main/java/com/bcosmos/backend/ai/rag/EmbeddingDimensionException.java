package com.bcosmos.backend.ai.rag;

/**
 * The configured vector width, the {@code code_chunk.embedding} column and the registry row of the embedding model
 * do not agree.
 *
 * <p>This is a configuration error, not a provider failure, and it must fail loudly: pgvector only compares vectors
 * of equal width, and a mismatch would otherwise appear as an empty result set ("retrieval found nothing") while
 * the real cause is a number in a settings file.</p>
 */
public class EmbeddingDimensionException extends RuntimeException {

    public EmbeddingDimensionException(String message) {
        super(message);
    }
}
