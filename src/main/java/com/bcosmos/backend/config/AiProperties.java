package com.bcosmos.backend.config;

import com.bcosmos.backend.persistence.LlmProvider;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the AI gateway is allowed to do (spec v2 section 7.1: timeouts, retries with backoff, provider
 * endpoints).
 *
 * <p>Secrets are read here and nowhere else: {@code api-key} is populated from an environment variable placeholder
 * in {@code application.yml}, never from a file, the database or the browser (ADR 0007, spec section 10). Values are
 * never logged or returned by the API.</p>
 *
 * @param enabled            global switch; {@code false} means nothing is sent to any provider
 * @param timeout            total budget of one call, including retries and backoff
 * @param connectTimeout     how long to wait for a connection to the provider
 * @param maxAttempts        provider attempts, including the first one
 * @param initialBackoff     wait before the second attempt
 * @param backoffMultiplier  factor the wait grows by after every failed attempt
 * @param maxOutputTokens    output cap used when a request does not set one
 * @param providers          per-provider endpoint and credential
 */
@ConfigurationProperties(prefix = "bcosmos.ai")
public record AiProperties(
        boolean enabled,
        Duration timeout,
        Duration connectTimeout,
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        int maxOutputTokens,
        Map<LlmProvider, ProviderSettings> providers) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 2048;

    public AiProperties {
        timeout = positiveOr(timeout, DEFAULT_TIMEOUT);
        connectTimeout = positiveOr(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        initialBackoff = positiveOr(initialBackoff, DEFAULT_INITIAL_BACKOFF);
        maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
        backoffMultiplier = backoffMultiplier < 1.0 ? DEFAULT_BACKOFF_MULTIPLIER : backoffMultiplier;
        maxOutputTokens = maxOutputTokens <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public ProviderSettings provider(LlmProvider provider) {
        return providers.getOrDefault(provider, ProviderSettings.EMPTY);
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    public record ProviderSettings(String baseUrl, String apiKey, String apiKeyEnv) {

        public static final ProviderSettings EMPTY = new ProviderSettings(null, null, null);

        public ProviderSettings {
            baseUrl = baseUrl == null ? null : baseUrl.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            apiKeyEnv = apiKeyEnv == null || apiKeyEnv.isBlank() ? null : apiKeyEnv.trim();
        }

        /** Whether a credential is available for this provider. */
        public boolean hasApiKey() {
            return !apiKey.isBlank();
        }
    }
}
