package com.bcosmos.backend.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One registry row as data, not as a JPA entity.
 *
 * <p>The store port speaks this record so the model registry can be tested without a database and so nothing
 * outside {@code persistence} handles a managed entity. {@code id} is {@code null} for a row that has not been
 * inserted yet.</p>
 */
public record LlmModelRecord(
        Long id,
        LlmProvider provider,
        String modelName,
        LlmModelKind kind,
        boolean enabled,
        boolean defaultModel,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public LlmModelRecord {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(costPer1kIn, "costPer1kIn");
        Objects.requireNonNull(costPer1kOut, "costPer1kOut");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** This row with an edit applied; a {@code null} argument leaves that field alone. */
    public LlmModelRecord withEdit(Boolean newEnabled, Boolean newDefaultModel, Integer newContextWindow,
            BigDecimal newCostPer1kIn, BigDecimal newCostPer1kOut, String newNotes, Instant now) {
        return new LlmModelRecord(
                id,
                provider,
                modelName,
                kind,
                newEnabled == null ? enabled : newEnabled,
                newDefaultModel == null ? defaultModel : newDefaultModel,
                newContextWindow == null ? contextWindow : newContextWindow,
                newCostPer1kIn == null ? costPer1kIn : newCostPer1kIn,
                newCostPer1kOut == null ? costPer1kOut : newCostPer1kOut,
                newNotes == null ? notes : newNotes,
                createdAt,
                now);
    }
}
