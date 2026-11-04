package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmOperation;
import com.bcosmos.backend.persistence.LlmUsageOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One row to write to {@code llm_usage}.
 *
 * <p>Failures are recorded too ({@link LlmUsageOutcome}), because "the model keeps answering JSON the schema
 * rejects" is a result the project has to be able to show, not swallow.</p>
 *
 * @param operation         chat, structured or embed
 * @param model             the model the call was made to; usage is only recorded once a model was resolved
 * @param promptVersion     versioned prompt that produced the request
 * @param tokensIn          input tokens the provider reported, 0 when it reported none
 * @param tokensOut         output tokens the provider reported, 0 when it reported none
 * @param latencyMs         wall time of the whole call, including retries and backoff
 * @param attempts          provider attempts, including retries
 * @param estimatedCostUsd  price estimate
 * @param outcome           how the call ended
 * @param errorCode         stable code when it did not succeed, otherwise {@code null}
 * @param createdAt         when the call started
 */
public record UsageRecord(
        LlmOperation operation,
        ModelRef model,
        String promptVersion,
        int tokensIn,
        int tokensOut,
        long latencyMs,
        int attempts,
        BigDecimal estimatedCostUsd,
        LlmUsageOutcome outcome,
        String errorCode,
        Instant createdAt) {

    public UsageRecord {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(estimatedCostUsd, "estimatedCostUsd");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
