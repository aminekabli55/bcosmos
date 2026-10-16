package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProviderRecord;
import com.bcosmos.backend.persistence.LlmProviderStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place that knows which provider has a credential and what it is (ADR 0038).
 *
 * <p>Two sources, in this order: a key the user stored through the settings screen — encrypted in
 * {@code llm_provider}, decrypted here and nowhere else — then the environment variable the provider row names.
 * A row that tells it takes no credential (a local Ollama or vLLM server) resolves to no key rather than a
 * failure, and the adapters leave the authorization header off.</p>
 *
 * <p>Everything else in the application asks this class, and this class is the only thing that ever returns a key
 * value. Nothing here logs one, and no method returns a key to anything that serializes a response.</p>
 */
public class ProviderCredentials {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentials.class);

    private final LlmProviderStore providers;
    private final SecretCipher cipher;
    private final HostEnvironment environment;

    public ProviderCredentials(LlmProviderStore providers, SecretCipher cipher, HostEnvironment environment) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /** The call-time view of a provider row; throws when the id does not exist. */
    public ProviderRef require(long providerId) {
        return ProviderRef.of(record(providerId));
    }

    /** Every provider, as the gateway sees them. */
    public List<ProviderRef> all() {
        return providers.findAll().stream().map(ProviderRef::of).toList();
    }

    /**
     * The credential for one call.
     *
     * @return the key, or {@code null} when this endpoint takes no credential
     * @throws MissingApiKeyException          when a key is required and neither source has one
     * @throws StoredCredentialUnreadableException when a stored key cannot be decrypted
     */
    public String resolve(ProviderRef provider) {
        LlmProviderRecord row = record(provider.id());
        if (row.hasStoredKey()) {
            return cipher.decrypt(row.apiKeyCiphertext(), row.code());
        }
        String fromEnvironment = environment.variable(row.apiKeyEnv());
        if (fromEnvironment != null) {
            return fromEnvironment;
        }
        if (!row.apiKeyRequired()) {
            return null;
        }
        throw new MissingApiKeyException(row.code(), row.apiKeyEnv());
    }

    /** Where this provider's credential comes from right now, without reading the value. */
    public CredentialSource source(ProviderRef provider) {
        LlmProviderRecord row = record(provider.id());
        if (row.hasStoredKey()) {
            return CredentialSource.STORED;
        }
        if (environment.variable(row.apiKeyEnv()) != null) {
            return CredentialSource.ENVIRONMENT;
        }
        return row.apiKeyRequired() ? CredentialSource.MISSING : CredentialSource.NOT_REQUIRED;
    }

    /** Whether a model on this provider could be called right now. */
    public boolean isConfigured(ProviderRef provider) {
        return source(provider) != CredentialSource.MISSING;
    }

    /** The endpoint root every adapter builds its calls from. */
    public String requireBaseUrl(ProviderRef provider) {
        String baseUrl = provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ProviderNotConfiguredException(provider.code(), "base URL");
        }
        return baseUrl;
    }

    /**
     * Every credential the process can read, so the redactor can strip them out of prompts and logs.
     *
     * <p>An unreadable stored key is skipped rather than raised: its value is unknown, so there is nothing to
     * redact, and a settings mistake must not stop an unrelated model from answering.</p>
     */
    public List<SecretRedactor.Secret> currentSecrets() {
        List<SecretRedactor.Secret> secrets = new ArrayList<>();
        for (LlmProviderRecord row : providers.findAll()) {
            try {
                String value = row.hasStoredKey()
                        ? cipher.decrypt(row.apiKeyCiphertext(), row.code())
                        : environment.variable(row.apiKeyEnv());
                if (value != null) {
                    secrets.add(new SecretRedactor.Secret(value, row.code()));
                }
            } catch (LlmException unreadable) {
                log.warn("provider {} has an unreadable stored key; it cannot be redacted either", row.code());
            }
        }
        return List.copyOf(secrets);
    }

    private LlmProviderRecord record(long providerId) {
        return providers.findById(providerId).orElseThrow(() -> new ProviderNotFoundException(providerId));
    }
}
