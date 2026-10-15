package com.bcosmos.backend.web.dto;

/**
 * Body of {@code POST /api/findings/{id}/suggestions} (spec v2 section 8).
 *
 * <p>Both fields are optional. {@code modelId} selects the chat model that writes the suggestion and
 * {@code embeddingModelId} the model that indexes and searches the run's code; leaving either out uses the
 * registry's default for that capability, which is why an embedding model can never be picked as the chat model.</p>
 *
 * @param modelId          chat model registry id, or {@code null} for the default chat model
 * @param embeddingModelId embedding model registry id, or {@code null} for the default embedding model
 */
public record SuggestionRequest(Long modelId, Long embeddingModelId) {
}
