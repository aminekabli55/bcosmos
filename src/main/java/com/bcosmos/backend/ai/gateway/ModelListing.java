package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One registry row as the settings screen needs it: the call-time view plus the switches and the state of the
 * credential.
 *
 * <p>The credential is reported as a boolean and an environment variable name. The value never leaves the process,
 * not even to the browser that configures the model (spec v2 sections 9 and 10).</p>
 *
 * @param credentialConfigured          whether the provider has an API key in the environment right now
 * @param credentialEnvironmentVariable which variable the key is expected in
 */
public record ModelListing(
        long id,
        LlmProvider provider,
        String modelName,
        LlmModelKind kind,
        boolean enabled,
        boolean defaultModel,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut,
        String notes,
        Instant updatedAt,
        boolean credentialConfigured,
        String credentialEnvironmentVariable) {

    /** The call-time view of this row. */
    public ModelRef toRef() {
        return new ModelRef(id, provider, modelName, kind, contextWindow, costPer1kIn, costPer1kOut);
    }
}
