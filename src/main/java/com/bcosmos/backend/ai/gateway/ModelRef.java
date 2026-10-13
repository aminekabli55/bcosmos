package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A registry row resolved into the values the gateway needs (spec v2 section 4.1, table {@code llm_model}).
 *
 * <p>It carries no credential: adapters ask {@link ProviderCredentials} for the key when they build a request, so a
 * model reference can be logged, returned by the API and stored on a usage row without leaking anything.</p>
 *
 * @param id            registry id
 * @param provider      which adapter talks to it
 * @param modelName     the provider's model name
 * @param kind          hosted or fine-tuned
 * @param contextWindow tokens the provider accepts in one request, for the UI's budget hints
 * @param costPer1kIn   estimated price per 1000 input tokens
 * @param costPer1kOut  estimated price per 1000 output tokens
 */
public record ModelRef(
        long id,
        LlmProvider provider,
        String modelName,
        LlmModelKind kind,
        int contextWindow,
        BigDecimal costPer1kIn,
        BigDecimal costPer1kOut) {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public ModelRef {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(costPer1kIn, "costPer1kIn");
        Objects.requireNonNull(costPer1kOut, "costPer1kOut");
    }

    /** How the model is named in a log line or a problem detail. */
    public String label() {
        return provider + "/" + modelName;
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
