package com.bcosmos.backend.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * One provider row as data, not as a JPA entity (spec v2 section 4.1, table {@code llm_provider}).
 *
 * <p>{@code apiKeyCiphertext} is opaque to everything outside {@code ProviderCredentials}: it is AES-GCM output, it
 * is never returned by the API, and the plaintext exists only for the length of one request (ADR 0038). A row with
 * no ciphertext takes its credential from the environment variable named by {@code apiKeyEnv}.</p>
 *
 * @param code             stable identifier used in logs, usage rows and problem details; immutable after creation
 * @param protocol         which adapter talks to this endpoint
 * @param baseUrl          the provider's documented API root
 * @param apiKeyEnv        environment variable consulted when no key is stored
 * @param apiKeyCiphertext encrypted key, or {@code null} when the key lives in the environment
 * @param apiKeyHint       last four characters of a stored key, for the settings screen
 * @param apiKeyRequired   false for a local server that takes no credential
 */
public record LlmProviderRecord(
        Long id,
        String code,
        String label,
        ProviderProtocol protocol,
        String baseUrl,
        String apiKeyEnv,
        String apiKeyCiphertext,
        String apiKeyHint,
        boolean apiKeyRequired,
        Instant createdAt,
        Instant updatedAt) {

    public LlmProviderRecord {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Whether a key the user pasted is stored for this provider. */
    public boolean hasStoredKey() {
        return apiKeyCiphertext != null && !apiKeyCiphertext.isBlank();
    }

    /**
     * This row with the editable metadata replaced; a {@code null} argument leaves that field alone.
     *
     * <p>The code is not among them: it is the stable identity and the associated data of the encrypted
     * credential, so renaming it would make a stored key undecryptable.</p>
     */
    public LlmProviderRecord withEdit(String newLabel, ProviderProtocol newProtocol, String newBaseUrl,
            String newApiKeyEnv, Boolean newApiKeyRequired, Instant now) {
        return new LlmProviderRecord(
                id,
                code,
                newLabel == null ? label : newLabel,
                newProtocol == null ? protocol : newProtocol,
                newBaseUrl == null ? baseUrl : newBaseUrl,
                newApiKeyEnv == null ? apiKeyEnv : newApiKeyEnv,
                apiKeyCiphertext,
                apiKeyHint,
                newApiKeyRequired == null ? apiKeyRequired : newApiKeyRequired,
                createdAt,
                now);
    }

    /** This row with its stored credential replaced, or cleared when both arguments are {@code null}. */
    public LlmProviderRecord withStoredKey(String ciphertext, String hint, Instant now) {
        return new LlmProviderRecord(id, code, label, protocol, baseUrl, apiKeyEnv, ciphertext, hint,
                apiKeyRequired, createdAt, now);
    }
}
