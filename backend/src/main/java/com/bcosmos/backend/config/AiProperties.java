package com.bcosmos.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the AI gateway is allowed to do (spec v2 section 7.1: timeouts, retries with backoff).
 *
 * <p>It no longer carries provider endpoints or credentials. Until Phase 11 each protocol had exactly one base URL
 * and one key here, which is what made the provider set a compile-time constant; both now live on the
 * {@code llm_provider} row they belong to, so DeepSeek, Groq, Ollama and OpenRouter can be registered side by side
 * and edited without a restart (ADR 0038).</p>
 *
 * <p>What is still configuration, and read here and nowhere else, is the master key that encrypts credentials the
 * user stores: {@code secret-key} comes from the environment (ADR 0007, spec section 10). An absent or empty value
 * is legitimate — it means a provider takes its key from the environment variable its row names, and pasting a new
 * key in the settings screen is refused with a message saying so.</p>
 *
 * <p>{@code secret-key-file} and {@code generate-secret-key} are the development convenience of ADR 0039: a key
 * file outside the repository that is generated on first start in the {@code dev} profile, so pasting a key in
 * Settings needs no shell step. Outside that profile nothing is generated and the environment stays the only
 * source.</p>
 *
 * @param enabled           global switch; {@code false} means nothing is sent to any provider
 * @param timeout           total budget of one call, including retries and backoff
 * @param connectTimeout    how long to wait for a connection to the provider
 * @param maxAttempts       provider attempts, including the first one
 * @param initialBackoff    wait before the second attempt
 * @param backoffMultiplier factor the wait grows by after every failed attempt
 * @param maxOutputTokens   output cap used when a request does not set one
 * @param secretKey         base64 of 32 random bytes, the key that protects stored credentials; wins over the file
 * @param secretKeyFile     where a locally kept master key lives; empty means no file at all
 * @param generateSecretKey whether a missing key file may be filled with a freshly generated key (dev only)
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
        String secretKey,
        String secretKeyFile,
        boolean generateSecretKey) {

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
        secretKey = secretKey == null ? "" : secretKey.trim();
        secretKeyFile = secretKeyFile == null ? "" : secretKeyFile.trim();
    }

    /** Whether a master key is configured in the environment, so a credential can be stored encrypted. */
    public boolean hasSecretKey() {
        return !secretKey.isBlank();
    }

    /**
     * The configured key file as a path, or {@code null} when no file is configured.
     *
     * <p>Resolved here rather than by binding a {@code Path} from the property, so an unset property is 'no file'
     * instead of a path to the working directory.</p>
     */
    public Path secretKeyPath() {
        return secretKeyFile.isBlank() ? null : Path.of(secretKeyFile);
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
