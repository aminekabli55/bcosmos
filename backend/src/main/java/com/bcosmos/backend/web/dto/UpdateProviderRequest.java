package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.ProviderEdit;
import com.bcosmos.backend.persistence.ProviderProtocol;
import jakarta.validation.constraints.Size;

/**
 * An edit of one provider (spec v2 section 8, {@code PUT /api/providers/{id}}).
 *
 * <p>Every field is optional and a missing one means "leave it". Two of them can legitimately be emptied: an empty
 * {@code apiKeyEnv} stops reading an environment variable, and an empty {@code apiKey} clears the stored key. The
 * code is absent because it is the stable identity — and the associated data of the encrypted credential, so
 * renaming it would make a stored key unreadable.</p>
 *
 * @param apiKey {@code null} leaves the stored key, an empty string clears it, anything else replaces it
 */
public record UpdateProviderRequest(
        @Size(max = 120) String label,
        ProviderProtocol protocol,
        @Size(max = 300) String baseUrl,
        @Size(max = 80) String apiKeyEnv,
        Boolean apiKeyRequired,
        @Size(max = 400) String apiKey) {

    public ProviderEdit toEdit() {
        return new ProviderEdit(label, protocol, baseUrl, apiKeyEnv, apiKeyRequired, apiKey);
    }
}
