package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One AI suggestion with the verdict that was computed for it (spec v2 sections 4.1 and 7.3).
 *
 * <p>{@code responseJson} is the whole structured answer and {@code validationJson} the verdict, stored as
 * {@code jsonb} so a reviewed suggestion can be read back later without asking a provider again — including the
 * failed checks, which are the interesting rows. {@code diff} is duplicated out of the answer because the review
 * screen shows it next to the verdict.</p>
 *
 * <p>No patch is ever applied to a repository from here: a suggestion is a proposal with evidence (spec v2
 * section 1.3).</p>
 */
@Entity
@Table(name = "suggestion")
public class SuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "finding_id", nullable = false)
    private Long findingId;

    /** Nullable: a suggestion outlives the registry row of the model that produced it. */
    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "prompt_version", nullable = false, length = 60)
    private String promptVersion;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "diff")
    private String diff;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SuggestionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_json", nullable = false)
    private String validationJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieval_json", nullable = false)
    private String retrievalJson;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** For JPA only. */
    protected SuggestionEntity() {
    }

    public SuggestionEntity(Long findingId, Long modelId, String promptVersion, String requestHash,
            String responseJson, String diff, String validationJson, String retrievalJson, long latencyMs,
            int tokensIn, int tokensOut, Instant createdAt) {
        this.findingId = Objects.requireNonNull(findingId, "findingId");
        this.modelId = modelId;
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion");
        this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
        this.responseJson = Objects.requireNonNull(responseJson, "responseJson");
        this.diff = diff;
        this.status = SuggestionStatus.PROPOSED;
        this.validationJson = Objects.requireNonNull(validationJson, "validationJson");
        this.retrievalJson = Objects.requireNonNull(retrievalJson, "retrievalJson");
        this.latencyMs = latencyMs;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Records a human's decision. Every decision is final: the row keeps the last one and when it was made. */
    public void review(SuggestionStatus decision, Instant now) {
        this.status = Objects.requireNonNull(decision, "decision");
        this.reviewedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() {
        return id;
    }

    public Long getFindingId() {
        return findingId;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public String getDiff() {
        return diff;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public String getValidationJson() {
        return validationJson;
    }

    public String getRetrievalJson() {
        return retrievalJson;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getTokensIn() {
        return tokensIn;
    }

    public int getTokensOut() {
        return tokensOut;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
