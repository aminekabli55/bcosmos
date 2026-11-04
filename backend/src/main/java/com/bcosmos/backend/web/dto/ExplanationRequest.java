package com.bcosmos.backend.web.dto;

/**
 * Body of {@code POST /api/findings/{id}/explanations} (spec v2 section 8).
 *
 * @param modelId registry id of the model to ask, or {@code null} for the default model
 */
public record ExplanationRequest(Long modelId) {
}
