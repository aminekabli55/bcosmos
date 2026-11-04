package com.bcosmos.backend.ai.gateway;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What one gateway call cost (spec v2 section 7.1: tokens, latency, estimated cost).
 *
 * @param tokensIn          input tokens as the provider reported them, 0 when it reports nothing
 * @param tokensOut         output tokens as the provider reported them, 0 when it reports nothing
 * @param latencyMs         wall time of the whole call, including retries and backoff
 * @param attempts          provider attempts, including retries
 * @param estimatedCostUsd  price estimate from the registry's per-1k prices; an estimate, not a bill
 */
public record LlmUsage(int tokensIn, int tokensOut, long latencyMs, int attempts, BigDecimal estimatedCostUsd) {

    public LlmUsage {
        Objects.requireNonNull(estimatedCostUsd, "estimatedCostUsd");
    }

    public int totalTokens() {
        return tokensIn + tokensOut;
    }
}
