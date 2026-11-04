package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A registry row resolved into the values the gateway needs (spec v2 section 4.1, table {@code llm_model}).
 *
 * <p>It carries no credential: {@code provider} is the resolved provider <em>row</em>, which holds the endpoint and
 * a reference to where the key lives but never the key itself. Adapters ask {@link ProviderCredentials} for the
 * value when they build a request, so a model reference can be logged, returned by the API and stored on a usage
 * row without leaking anything (ADR 0038).</p>
 *
 * @param id            registry id
 * @param provider      the provider row that owns the endpoint and the credential
 * @param modelName     the provider's model name
 * @param kind               hosted or fine-tuned
 * @param capability         chat or embedding; the gateway only ever asks this ref for what it can do
 * @param embeddingDimension vector width requested from the provider, or {@code null} for a chat model
 * @param contextWindow      tokens the provider accepts in one request, for the UI's budget hints
 * @param costPer1kIn        estimated price per 1000 input tokens
 * @param costPer1kOut       estimated price per 1000 output tokens
 */
public record ModelRef(
        long id,
        ProviderRef provider,
        String modelName,
        LlmModelKind kind,
        LlmModelCapability capability,
        Integer embeddingDimension,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut) {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public ModelRef {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(kind, "kind");
        capability = capability == null ? LlmModelCapability.CHAT : capability;
        Objects.requireNonNull(costPer1kIn, "costPer1kIn");
        Objects.requireNonNull(costPer1kOut, "costPer1kOut");
    }

    /** True when this model may be asked for vectors. */
    public boolean embeddingCapable() {
        return capability == LlmModelCapability.EMBEDDING;
    }

    /** How the model is named in a log line or a problem detail. */
    public String label() {
        return provider.code() + "/" + modelName;
    }

    /**
     * Estimated price of one call, from the prices in the registry.
     *
     * <p>It is an estimate: prices change and free tiers exist, which is exactly why the row says {@code estimated}
     * (spec v2 section 7.1).</p>
     */
    public BigDecimal estimatedCostUsd(int tokensIn, int tokensOut) {
        BigDecimal input = costPer1kIn.multiply(BigDecimal.valueOf(tokensIn));
        BigDecimal output = costPer1kOut.multiply(BigDecimal.valueOf(tokensOut));
        return input.add(output).divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }
}
