package com.bcosmos.backend.config;

import com.bcosmos.backend.ai.gateway.GeminiAdapter;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.LlmException;
import com.bcosmos.backend.ai.gateway.LlmGateway;
import com.bcosmos.backend.ai.gateway.LlmProviderAdapter;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.ai.gateway.OpenAiCompatibleAdapter;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.ProviderCredentials;
import com.bcosmos.backend.ai.gateway.SecretRedactor;
import com.bcosmos.backend.ai.gateway.UsageRecorder;
import com.bcosmos.backend.persistence.LlmModelStore;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    public ProviderCredentials providerCredentials(AiProperties properties) {
        return new ProviderCredentials(properties);
    }

    @Bean
    public SecretRedactor secretRedactor(ProviderCredentials credentials) {
        return SecretRedactor.from(credentials);
    }

    @Bean
    public ModelRegistry modelRegistry(LlmModelStore models, ProviderCredentials credentials, Clock clock) {
        return new ModelRegistry(models, credentials, clock);
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
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(properties.initialBackoff(),
                        properties.backoffMultiplier()))
                .retryOnException(failure -> failure instanceof LlmException llm && llm.retryable())
                .failAfterMaxAttempts(false)
                .build();
        return Retry.of("llm-gateway", config);
    }

    /**
     * The single model interface the rest of the application sees (ADR 0007).
     *
     * <p>Returned as {@link LlmClient}, so nothing outside this package can reach a provider directly.</p>
     */
    @Bean
    public LlmClient llmClient(AiProperties properties, ModelRegistry models, List<LlmProviderAdapter> adapters,
            UsageRecorder usage, SecretRedactor redactor, ObjectMapper json, Retry llmRetry, Clock clock) {
        return new LlmGateway(properties, models, adapters, usage, redactor, json, llmRetry, clock);
    }
}
