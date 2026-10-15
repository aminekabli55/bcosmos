package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.ModelListing;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One model registry row (spec v2 section 8, {@code GET /api/models}).
 *
 * <p>{@code credentialConfigured} and {@code credentialEnvironmentVariable} tell the settings screen what to ask the
 * user for. The key itself is never part of any response (spec section 10).</p>
 */
public record ModelResponse(
        long id,
        LlmProvider provider,
        String modelName,
        LlmModelKind kind,
        LlmModelCapability capability,
        Integer embeddingDimension,
        boolean enabled,
        boolean defaultModel,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes,
        Instant updatedAt,
        boolean credentialConfigured,
        String credentialEnvironmentVariable) {

    public static ModelResponse of(ModelListing listing) {
        return new ModelResponse(
                listing.id(),
                listing.provider(),
                listing.modelName(),
                listing.kind(),
                listing.capability(),
                listing.embeddingDimension(),
                listing.enabled(),
                listing.defaultModel(),
                listing.contextWindow(),
                listing.costPer1kIn(),
                listing.costPer1kOut(),
                listing.notes(),
                listing.updatedAt(),
                listing.credentialConfigured(),
                listing.credentialEnvironmentVariable());
    }
}
