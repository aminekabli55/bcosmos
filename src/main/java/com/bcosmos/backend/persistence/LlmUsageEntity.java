package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One model call, recorded whether it worked or not (spec v2 section 7.1: tokens, latency, estimated cost).
 *
 * <p>Provider and model name are stored next to the registry id so a usage row stays readable after the registry
 * row is renamed, and the id is nullable so the history survives the model being removed.</p>
 */
@Entity
@Table(name = "llm_usage")
public class LlmUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id")
    private Long modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private LlmProvider provider;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    private LlmOperation operation;

    @Column(name = "prompt_version", length = 60)
    private String promptVersion;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private LlmUsageOutcome outcome;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected LlmUsageEntity() {
    }

    public LlmUsageEntity(Long modelId, LlmProvider provider, String modelName, LlmOperation operation,
            String promptVersion, int tokensIn, int tokensOut, long latencyMs, int attempts,
            BigDecimal estimatedCostUsd, LlmUsageOutcome outcome, String errorCode, Instant createdAt) {
        this.modelId = modelId;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.promptVersion = promptVersion;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.latencyMs = latencyMs;
        this.attempts = attempts;
        this.estimatedCostUsd = Objects.requireNonNull(estimatedCostUsd, "estimatedCostUsd");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.errorCode = errorCode;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getModelId() {
        return modelId;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public LlmOperation getOperation() {
        return operation;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public int getTokensIn() {
        return tokensIn;
    }

    public int getTokensOut() {
        return tokensOut;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getAttempts() {
        return attempts;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public LlmUsageOutcome getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
