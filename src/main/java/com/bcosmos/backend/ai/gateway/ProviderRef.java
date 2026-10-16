package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProviderRecord;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.util.Objects;

/**
 * A provider row resolved into the values the gateway needs: where to send, how to talk, and which credential
 * source to consult.
 *
 * <p>It carries no key material — not the key, not even the ciphertext — so a reference can be logged, put on a
 * usage row or returned by the API. The key is resolved per call by {@link ProviderCredentials} (ADR 0038).</p>
 *
 * @param code           stable identifier, used in logs, usage rows and problem details
 * @param protocol       which adapter handles this endpoint
 * @param baseUrl        the provider's API root; the protocol appends its own paths
 * @param apiKeyEnv      environment variable consulted when no key is stored, or {@code null}
 * @param apiKeyRequired false for a local server that takes no credential
 */
public record ProviderRef(
        long id,
        String code,
        String label,
        ProviderProtocol protocol,
        String baseUrl,
        String apiKeyEnv,
        boolean apiKeyRequired) {

    public ProviderRef {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(baseUrl, "baseUrl");
    }

    /** The call-time view of a stored provider row. */
    public static ProviderRef of(LlmProviderRecord provider) {
        return new ProviderRef(provider.id(), provider.code(), provider.label(), provider.protocol(),
                provider.baseUrl(), provider.apiKeyEnv(), provider.apiKeyRequired());
    }
}
