package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.ProviderProtocol;

/**
 * An edit of one provider (spec v2 section 8, {@code PUT /api/providers/{id}}).
 *
 * <p>A {@code null} field means "leave it as it is". The two string fields that can legitimately become empty —
 * the environment variable name and the stored key — use an empty string to mean "clear this", which is why they
 * are not simply null. {@code code} is absent on purpose: it is the stable identity and the associated data of the
 * encrypted credential.</p>
 *
 * @param apiKey {@code null} leaves the stored key, an empty string clears it, anything else replaces it
 */
public record ProviderEdit(
        String label,
        ProviderProtocol protocol,
        String baseUrl,
        String apiKeyEnv,
        Boolean apiKeyRequired,
        String apiKey) {
}
