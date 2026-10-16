package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.CredentialSource;
import com.bcosmos.backend.ai.gateway.ModelListing;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One model registry row (spec v2 section 8, {@code GET /api/models}).
 *
 * <p>{@code provider} is the provider's code — {@code deepseek}, {@code claude}, {@code local-openai} — and it is
 * what the settings screen and the model selectors show. {@code credentialSource} and {@code credentialHint} tell
 * the screen whether a key is available and which one is in place; the key itself is never part of any response
 * (spec section 10).</p>
 *
 * @param credentialHint                last four characters of a stored key, or {@code null}
 * @param credentialEnvironmentVariable variable the provider row names, or {@code null}
 */
public record ModelResponse(
        long id,
        long providerId,
        String provider,
        String providerLabel,
        ProviderProtocol providerProtocol,
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
        CredentialSource credentialSource,
        String credentialHint,
        String credentialEnvironmentVariable) {

    public static ModelResponse of(ModelListing listing) {
        return new ModelResponse(
                listing.id(),
                listing.provider().id(),
                listing.provider().code(),
                listing.provider().label(),
                listing.provider().protocol(),
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
                listing.credentialSource(),
                listing.credentialHint(),
                listing.credentialEnvironmentVariable());
    }
}
