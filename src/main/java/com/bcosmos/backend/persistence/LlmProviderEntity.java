package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One callable provider (spec v2 section 4.1, table {@code llm_provider}).
 *
 * <p>The credential column holds ciphertext or nothing. The key the user types is encrypted before it gets here,
 * with a master key that comes from the environment (ADR 0038), so a database dump carries no usable credential.
 * Only the last four characters are kept in the clear, and only so the settings screen can say which key is in
 * place.</p>
 */
@Entity
@Table(name = "llm_provider")
public class LlmProviderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 40)
    private ProviderProtocol protocol;

    @Column(name = "base_url", nullable = false, length = 300)
    private String baseUrl;

    @Column(name = "api_key_env", length = 80)
    private String apiKeyEnv;

    /** AES-GCM output, never a plaintext key. */
    @Column(name = "api_key_ciphertext")
    private String apiKeyCiphertext;

    @Column(name = "api_key_hint", length = 16)
    private String apiKeyHint;

    @Column(name = "api_key_required", nullable = false)
    private boolean apiKeyRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected LlmProviderEntity() {
    }

    public LlmProviderEntity(String code, String label, ProviderProtocol protocol, String baseUrl, String apiKeyEnv,
            String apiKeyCiphertext, String apiKeyHint, boolean apiKeyRequired, Instant now) {
        this.code = Objects.requireNonNull(code, "code");
        this.label = Objects.requireNonNull(label, "label");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.apiKeyEnv = apiKeyEnv;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyHint = apiKeyHint;
        this.apiKeyRequired = apiKeyRequired;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Applies an edit from the settings screen; {@code null} means "leave as it is". */
    public void edit(String newLabel, ProviderProtocol newProtocol, String newBaseUrl, String newApiKeyEnv,
            Boolean newApiKeyRequired, Instant now) {
        if (newLabel != null) {
            this.label = newLabel;
        }
        if (newProtocol != null) {
            this.protocol = newProtocol;
        }
        if (newBaseUrl != null) {
            this.baseUrl = newBaseUrl;
        }
        if (newApiKeyEnv != null) {
            // An empty string is how the screen removes the environment variable reference.
            this.apiKeyEnv = newApiKeyEnv.isBlank() ? null : newApiKeyEnv;
        }
        if (newApiKeyRequired != null) {
            this.apiKeyRequired = newApiKeyRequired;
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Replaces the stored credential, or clears it when both arguments are {@code null}. */
    public void storeKey(String ciphertext, String hint, Instant now) {
        this.apiKeyCiphertext = ciphertext;
        this.apiKeyHint = hint;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public ProviderProtocol getProtocol() {
        return protocol;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public String getApiKeyHint() {
        return apiKeyHint;
    }

    public boolean isApiKeyRequired() {
        return apiKeyRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
