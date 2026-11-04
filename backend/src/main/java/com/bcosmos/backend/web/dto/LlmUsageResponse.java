package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.gateway.LlmUsage;
import java.math.BigDecimal;

/**
 * What one call cost (spec v2 section 7.1). Shown next to the answer so a model comparison is about facts, not
 * impressions.
 *
 * @param estimatedCostUsd an estimate from the registry's prices, not a bill
 */
public record LlmUsageResponse(
        int tokensIn,
        int tokensOut,
        long latencyMs,
        int attempts,
        BigDecimal estimatedCostUsd) {

    public static LlmUsageResponse of(LlmUsage usage) {
        return new LlmUsageResponse(usage.tokensIn(), usage.tokensOut(), usage.latencyMs(), usage.attempts(),
                usage.estimatedCostUsd());
    }
}
