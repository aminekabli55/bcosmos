package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The single place that knows which provider has a credential (spec v2 section 10: API keys come from environment
 * variables only).
 *
 * <p>Everything else — the registry, the adapters, the redactor, the settings screen — asks this class, and this
 * class is the only thing that ever returns the key value.</p>
 */
public class ProviderCredentials {

    private final AiProperties properties;

    public ProviderCredentials(AiProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /** Whether the provider has a usable credential in the current environment. */
    public boolean isConfigured(LlmProvider provider) {
        return properties.provider(provider).hasApiKey();
    }

    /** The name of the environment variable that carries the key, for messages and documentation. */
    public String environmentVariable(LlmProvider provider) {
        String configured = properties.provider(provider).apiKeyEnv();
        return configured == null ? defaultEnvironmentVariable(provider) : configured;
    }

    /** The key itself, or a clear failure naming the environment variable that should hold it. */
    public String require(LlmProvider provider) {
        String apiKey = properties.provider(provider).apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(provider, environmentVariable(provider));
        }
        return apiKey;
    }

    /** The provider root every adapter builds its calls from. */
    public String baseUrl(LlmProvider provider) {
        String baseUrl = properties.provider(provider).baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ProviderNotConfiguredException(provider, "bcosmos.ai.providers." + configurationKey(provider)
                    + ".base-url");
        }
        return baseUrl;
    }

    /** Every configured credential, so the redactor can strip them out of prompts and logs. */
    public List<String> configuredSecrets() {
        List<String> secrets = new ArrayList<>();
        for (LlmProvider provider : LlmProvider.values()) {
            String apiKey = properties.provider(provider).apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                secrets.add(apiKey);
            }
        }
        return List.copyOf(secrets);
    }

    /** Kebab-case key used in {@code application.yml} for a provider. */
    public static String configurationKey(LlmProvider provider) {
        return provider.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    private static String defaultEnvironmentVariable(LlmProvider provider) {
        return switch (provider) {
            case GEMINI -> "GEMINI_API_KEY";
            case OPENAI_COMPATIBLE -> "OPENAI_API_KEY";
        };
    }
}
