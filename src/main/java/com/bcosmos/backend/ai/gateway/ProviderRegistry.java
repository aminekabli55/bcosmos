package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProviderRecord;
import com.bcosmos.backend.persistence.LlmProviderStore;
import com.bcosmos.backend.persistence.ProviderProtocol;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding and changing providers, which is what stopped being a code change in Phase 11 (spec v2 sections 7.1 and 8).
 *
 * <p>The whole point of this class is that "support DeepSeek" is now a row: a code, a protocol that picks an
 * adapter, a base URL and a credential. Validation lives here rather than in the controller so the same rules hold
 * for the API and for a test that calls this class directly.</p>
 *
 * <p>Two things this class will not do: it will not store a credential in the clear (no master key means a refusal,
 * never a plaintext column), and it will not hand a stored key back to a caller — {@link ProviderListing} carries a
 * four-character hint and nothing else.</p>
 */
public class ProviderRegistry {

    /** Lowercase, starts with a letter or digit; the stable identifier in logs and usage rows. */
    private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9._-]{1,59}");

    /** An environment variable name: uppercase by convention, but any valid shell identifier is accepted. */
    private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,79}");

    private static final int MAX_LABEL = 120;
    private static final int MAX_BASE_URL = 300;
    private static final int HINT_CHARACTERS = 4;

    private final LlmProviderStore providers;
    private final LlmModelStore models;
    private final ProviderCredentials credentials;
    private final SecretCipher cipher;
    private final Clock clock;

    public ProviderRegistry(LlmProviderStore providers, LlmModelStore models, ProviderCredentials credentials,
            SecretCipher cipher, Clock clock) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.models = Objects.requireNonNull(models, "models");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Every provider, as the settings screen lists them. */
    @Transactional(readOnly = true)
    public List<ProviderListing> listing() {
        return providers.findAll().stream().map(this::toListing).toList();
    }

    /** The call-time view of one provider. */
    @Transactional(readOnly = true)
    public ProviderRef require(long providerId) {
        return credentials.require(providerId);
    }

    /** Every provider the gateway may call, as call-time views. */
    @Transactional(readOnly = true)
    public List<ProviderRef> all() {
        return credentials.all();
    }

    /**
     * Registers a provider.
     *
     * <p>A pasted key is encrypted here, before it reaches the store, and the row keeps only its last four
     * characters in the clear. A provider can be registered with no key at all: that is the normal state for an
     * endpoint whose key will come from the environment on the next deployment.</p>
     */
    @Transactional
    public ProviderListing create(ProviderDraft draft) {
        String code = normalizeCode(draft.code());
        if (providers.findByCode(code).isPresent()) {
            throw new DuplicateProviderException(code);
        }
        String label = requireLabel(draft.label());
        ProviderProtocol protocol = requireProtocol(draft.protocol());
        String baseUrl = normalizeBaseUrl(draft.baseUrl());
        String apiKeyEnv = normalizeEnvironment(draft.apiKeyEnv());
        boolean apiKeyRequired = draft.apiKeyRequired() == null || draft.apiKeyRequired();

        String ciphertext = null;
        String hint = null;
        if (hasText(draft.apiKey())) {
            String key = draft.apiKey().trim();
            ciphertext = cipher.encrypt(key, code);
            hint = hintOf(key);
        }

        Instant now = clock.instant();
        LlmProviderRecord saved = providers.save(new LlmProviderRecord(null, code, label, protocol, baseUrl,
                apiKeyEnv, ciphertext, hint, apiKeyRequired, now, now));
        return toListing(saved);
    }

    /**
     * Applies an edit from the settings screen.
     *
     * <p>The code cannot change: it is what usage rows and log lines name, and it is the associated data of the
     * encrypted credential, so renaming it would silently make a stored key unreadable.</p>
     */
    @Transactional
    public ProviderListing edit(long providerId, ProviderEdit edit) {
        LlmProviderRecord row = record(providerId);
        String label = edit.label() == null ? null : requireLabel(edit.label());
        String baseUrl = edit.baseUrl() == null ? null : normalizeBaseUrl(edit.baseUrl());
        String apiKeyEnv = normalizeEnvironment(edit.apiKeyEnv());

        LlmProviderRecord updated = row.withEdit(label, edit.protocol(), baseUrl, apiKeyEnv, edit.apiKeyRequired(),
                clock.instant());
        if (edit.apiKey() != null) {
            Instant now = clock.instant();
            if (edit.apiKey().isBlank()) {
                updated = updated.withStoredKey(null, null, now);
            } else {
                String key = edit.apiKey().trim();
                updated = updated.withStoredKey(cipher.encrypt(key, row.code()), hintOf(key), now);
            }
        }
        return toListing(providers.save(updated));
    }

    /**
     * Removes a provider that no registry row references.
     *
     * <p>Nothing about the original repository or the existing runs is touched, and the usage history keeps its own
     * copy of the provider code, so reports stay readable after a provider is gone.</p>
     */
    @Transactional
    public void delete(long providerId) {
        LlmProviderRecord row = record(providerId);
        long inUse = models.countByProvider(providerId);
        if (inUse > 0) {
            throw new ProviderInUseException(row.code(), inUse);
        }
        providers.delete(providerId);
    }

    private LlmProviderRecord record(long providerId) {
        return providers.findById(providerId).orElseThrow(() -> new ProviderNotFoundException(providerId));
    }

    private ProviderListing toListing(LlmProviderRecord row) {
        ProviderRef ref = ProviderRef.of(row);
        return new ProviderListing(
                row.id(),
                row.code(),
                row.label(),
                row.protocol(),
                row.baseUrl(),
                row.apiKeyRequired(),
                credentials.source(ref),
                row.apiKeyHint(),
                row.apiKeyEnv(),
                models.countByProvider(row.id()),
                row.updatedAt());
    }

    private static String normalizeCode(String code) {
        if (!hasText(code)) {
            throw new InvalidProviderUpdateException("code is required, for example 'deepseek'");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new InvalidProviderUpdateException("code '" + normalized + "' is not usable: lowercase letters, "
                    + "digits, dots, dashes and underscores, 2 to 60 characters, and it must start with a letter or "
                    + "a digit");
        }
        return normalized;
    }

    private static String requireLabel(String label) {
        if (!hasText(label)) {
            throw new InvalidProviderUpdateException("label is required, for example 'DeepSeek'");
        }
        String trimmed = label.trim();
        if (trimmed.length() > MAX_LABEL) {
            throw new InvalidProviderUpdateException("label is longer than " + MAX_LABEL + " characters");
        }
        return trimmed;
    }

    private static ProviderProtocol requireProtocol(ProviderProtocol protocol) {
        if (protocol == null) {
            throw new InvalidProviderUpdateException("protocol is required: GEMINI or OPENAI_COMPATIBLE");
        }
        return protocol;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (!hasText(baseUrl)) {
            throw new InvalidProviderUpdateException("baseUrl is required, for example "
                    + "'https://api.deepseek.com/v1'");
        }
        String trimmed = baseUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new InvalidProviderUpdateException("baseUrl must start with http:// or https://");
        }
        // Trailing slashes are stripped so a path is never appended onto "//"; the protocol owns the paths.
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.length() > MAX_BASE_URL) {
            throw new InvalidProviderUpdateException("baseUrl is longer than " + MAX_BASE_URL + " characters");
        }
        return trimmed;
    }

    /**
     * Normalizes an environment variable reference.
     *
     * <p>An empty result means "do not read a variable": on create there is then simply no fallback, and on edit it
     * clears an existing reference.</p>
     */
    private static String normalizeEnvironment(String apiKeyEnv) {
        if (apiKeyEnv == null) {
            return null;
        }
        String trimmed = apiKeyEnv.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!ENVIRONMENT_VARIABLE.matcher(trimmed).matches()) {
            throw new InvalidProviderUpdateException("'" + trimmed + "' is not a usable environment variable name");
        }
        return trimmed;
    }

    private static String hintOf(String key) {
        return key.length() <= HINT_CHARACTERS ? key : key.substring(key.length() - HINT_CHARACTERS);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
