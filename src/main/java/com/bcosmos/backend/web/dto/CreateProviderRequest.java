package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.ProviderDraft;
import com.bcosmos.backend.persistence.ProviderProtocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new provider (spec v2 section 8, {@code POST /api/providers}).
 *
 * <p>{@code apiKey} is the only field that carries a credential, it travels once, and it is encrypted before it is
 * stored. It is never echoed back: the response reports a four-character hint instead.</p>
 *
 * @param protocol       which wire shape the endpoint speaks; it selects the adapter
 * @param apiKeyEnv      environment variable to fall back on when no key is pasted
 * @param apiKeyRequired {@code null} means yes; a local Ollama or vLLM server says no
 */
public record CreateProviderRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 120) String label,
        @NotNull ProviderProtocol protocol,
        @NotBlank @Size(max = 300) String baseUrl,
        @Size(max = 80) String apiKeyEnv,
        Boolean apiKeyRequired,
        @Size(max = 400) String apiKey) {

    public ProviderDraft toDraft() {
        return new ProviderDraft(code, label, protocol, baseUrl, apiKeyEnv, apiKeyRequired, apiKey);
    }
}
