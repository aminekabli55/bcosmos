package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One registry row as the settings screen needs it: the call-time view plus the switches and the state of the
 * credential.
 *
 * <p>The provider is reported as its code, label and protocol, the credential as a source and a four-character
 * hint. The value never leaves the process, not even to the browser that configures the model (spec v2 sections 9
 * and 10).</p>
 *
 * @param credentialSource              where this provider's key comes from, or why it is missing
 * @param credentialHint                last four characters of a stored key, or {@code null}
 * @param credentialEnvironmentVariable variable the provider row names, or {@code null}
 */
public record ModelListing(
        long id,
        ProviderRef provider,
        String modelName,
        LlmModelKind kind,
        LlmModelCapability capability,
        Integer embeddingDimension,
        boolean enabled,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes,
        Instant updatedAt,
        CredentialSource credentialSource,
        String credentialHint,
        String credentialEnvironmentVariable) {

    /** The call-time view of this row. */
    public ModelRef toRef() {
        return new ModelRef(id, provider, modelName, kind, capability, embeddingDimension, contextWindow,
                costPer1kIn, costPer1kOut);
    }
}
