package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.ProviderProtocol;

/**
 * A new provider, as the settings screen sends it (spec v2 section 8).
 *
 * @param code           identifier the user chooses; lowercased, immutable afterwards
 * @param baseUrl        the provider's API root, for example {@code https://api.deepseek.com/v1}
 * @param apiKeyEnv      environment variable to fall back on, or {@code null}
 * @param apiKeyRequired {@code null} means "yes", which is right for every hosted vendor
 * @param apiKey         plaintext key, encrypted before it is stored; {@code null} when none is pasted
 */
public record ProviderDraft(
        String code,
        String label,
        ProviderProtocol protocol,
        String baseUrl,
        String apiKeyEnv,
        Boolean apiKeyRequired,
        String apiKey) {
}
