package com.bcosmos.backend.config;

import com.bcosmos.backend.ai.gateway.AiEnablement;
import com.bcosmos.backend.ai.gateway.GeminiAdapter;
import com.bcosmos.backend.ai.gateway.HostEnvironment;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.LlmException;
import com.bcosmos.backend.ai.gateway.LlmGateway;
import com.bcosmos.backend.ai.gateway.LlmProviderAdapter;
import com.bcosmos.backend.ai.gateway.MasterKeySource;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.ai.gateway.OpenAiCompatibleAdapter;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.ProviderCredentials;
import com.bcosmos.backend.ai.gateway.ProviderRegistry;
import com.bcosmos.backend.ai.gateway.SecretCipher;
import com.bcosmos.backend.ai.gateway.SecretRedactor;
import com.bcosmos.backend.ai.gateway.UsageRecorder;
import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProviderStore;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    /**
     * The one place that may read credentials out of the environment.
     *
     * <p>Backed by Spring's {@code Environment} rather than {@code System.getenv} directly: it resolves the same
     * process environment variables, and an integration test can supply one as a property instead of depending on
     * the machine it runs on.</p>
     */
    @Bean
    public HostEnvironment hostEnvironment(Environment environment) {
        return name -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            String value = environment.getProperty(name);
            return value == null || value.isBlank() ? null : value;
        };
    }

    /**
     * The master key that protects credentials stored through the settings screen (ADR 0038, ADR 0039).
     *
     * <p>Built eagerly on purpose: a malformed key fails the startup with a message naming the property, instead of
     * failing at the first call that needed to decrypt something.</p>
     */
    @Bean
    public SecretCipher secretCipher(AiProperties properties) {
        return MasterKeySource.resolve(properties.secretKey(), properties.secretKeyPath(),
                properties.generateSecretKey());
    }

    @Bean
    public ProviderCredentials providerCredentials(LlmProviderStore providers, SecretCipher cipher,
            HostEnvironment environment) {
        return new ProviderCredentials(providers, cipher, environment);
    }

    @Bean
    public SecretRedactor secretRedactor(ProviderCredentials credentials) {
        return SecretRedactor.from(credentials);
    }

    @Bean
    public ModelRegistry modelRegistry(LlmModelStore models, LlmProviderStore providers,
            ProviderCredentials credentials, Clock clock) {
        return new ModelRegistry(models, providers, credentials, clock);
    }

    @Bean
    public ProviderRegistry providerRegistry(LlmProviderStore providers, LlmModelStore models,
            ProviderCredentials credentials, SecretCipher cipher, Clock clock) {
        return new ProviderRegistry(providers, models, credentials, cipher, clock);
    }

    @Bean
    public PromptTemplateLoader promptTemplateLoader(ResourceLoader resourceLoader, ObjectMapper json) {
        return new PromptTemplateLoader(resourceLoader, json);
    }

    @Bean
    public LlmProviderAdapter geminiAdapter(AiProperties properties, ProviderCredentials credentials,
            ObjectMapper json) {
        return new GeminiAdapter(properties, credentials, json);
    }

    @Bean
    public LlmProviderAdapter openAiCompatibleAdapter(AiProperties properties, ProviderCredentials credentials,
            ObjectMapper json) {
        return new OpenAiCompatibleAdapter(properties, credentials, json);
    }

    /**
     * Retry policy for provider calls.
     *
     * <p>Only failures that carry {@code retryable} are retried — a rejected credential or a rejected prompt is not
     * going to succeed on the second try, and the call is paid for every time it is sent.</p>
     */
    @Bean
    public Retry llmRetry(AiProperties properties) {
        return retryFor(properties);
    }

    /**
     * The one definition of the retry policy, so tests exercise what production runs.
     *
     * <p>It is not a bean factory of its own: only the attempt count and the backoff come from configuration, and
     * tests need exactly the same object.</p>
     */
    public static Retry retryFor(AiProperties properties) {
        IntervalFunction backoff = IntervalFunction.ofExponentialBackoff(properties.initialBackoff(),
                properties.backoffMultiplier());
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalBiFunction((attempt, outcome) -> waitBeforeRetry(properties, backoff, attempt, outcome))
                .retryOnException(failure -> failure instanceof LlmException llm && llm.retryable())
                .failAfterMaxAttempts(false)
                .build();
        return Retry.of("llm-gateway", config);
    }

    /**
     * How long to wait before the next attempt.
     *
     * <p>A provider that answers 429 usually says when to come back, in a {@code Retry-After} header or (at Google)
     * in the error body, and that number beats any guess we could make: a per-minute quota window is still closed
     * long after 500 ms, so the retry that the backoff buys is a retry that cannot succeed, paid for every time
     * (ADR 0040).</p>
     *
     * <p>An informed wait is only taken when the whole budget of one call can afford it. A provider asking for an
     * hour while {@code bcosmos.ai.timeout} says 30s is not a reason to hold a request for an hour - the retry goes
     * ahead at once and the failure itself carries the wait, so the operator reads "it asks to wait 1h 0m" instead of
     * waiting for it.</p>
     *
     * <p>Everything else keeps the exponential backoff.</p>
     */
    private static long waitBeforeRetry(AiProperties properties, IntervalFunction backoff, Integer attempt,
            Either<Throwable, Object> outcome) {
        if (outcome.isLeft() && outcome.getLeft() instanceof LlmException failure) {
            Duration instructed = failure.instructedWait();
            if (!instructed.isZero()) {
                return instructed.compareTo(properties.timeout()) <= 0 ? instructed.toMillis() : 0L;
            }
        }
        return backoff.apply(attempt);
    }

    /**
     * The single model interface the rest of the application sees (ADR 0007).
     *
     * <p>Returned as {@link LlmClient}, so nothing outside this package can reach a provider directly.</p>
     */
    @Bean
    public LlmClient llmClient(AiProperties properties, AiEnablement enablement, ModelRegistry models,
            List<LlmProviderAdapter> adapters, UsageRecorder usage, SecretRedactor redactor, ObjectMapper json,
            Retry llmRetry, Clock clock) {
        return new LlmGateway(properties, enablement, models, adapters, usage, redactor, json, llmRetry, clock);
    }
}
