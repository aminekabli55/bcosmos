package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Three-layer evidence-based commit review (spec v2 section 7.6, table {@code commit_review}).
 *
 * <p>Contains:
 * 1. Facts (no AI): files changed, findings delta (NEW/RESOLVED/PERSISTENT/UNCERTAIN), affected dependents, complexity.
 * 2. Risk signals: deterministic heuristics (hotspots touched, high findings added, test modifications).
 * 3. AI review summary: optional plain markdown summary with verified file/line citations.</p>
 */
@Entity
@Table(name = "commit_review")
public class CommitReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "from_commit_hash", nullable = false, length = 64)
    private String fromCommitHash;

    @Column(name = "to_commit_hash", nullable = false, length = 64)
    private String toCommitHash;

    @Column(name = "files_changed_count", nullable = false)
    private int filesChangedCount;

    @Column(name = "new_findings_count", nullable = false)
    private int newFindingsCount;

    @Column(name = "resolved_findings_count", nullable = false)
    private int resolvedFindingsCount;

    @Column(name = "persistent_findings_count", nullable = false)
    private int persistentFindingsCount;

    @Column(name = "uncertain_findings_count", nullable = false)
    private int uncertainFindingsCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "facts_json", nullable = false)
    private String factsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_signals_json", nullable = false)
    private String riskSignalsJson;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "what_to_check_json")
    private String whatToCheckJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tests_to_run_json")
    private String testsToRunJson;

    @Column(name = "ai_model_name", length = 100)
    private String aiModelName;

    @Column(name = "ai_model_id")
    private Long aiModelId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected CommitReviewEntity() {
    }

    public CommitReviewEntity(
            Long repositoryId,
            String fromCommitHash,
            String toCommitHash,
            int filesChangedCount,
            int newFindingsCount,
            int resolvedFindingsCount,
            int persistentFindingsCount,
            int uncertainFindingsCount,
            String factsJson,
            String riskSignalsJson,
            String aiSummary,
            String whatToCheckJson,
            String testsToRunJson,
            String aiModelName,
            Long aiModelId,
            Instant createdAt) {
        this.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId");
        this.fromCommitHash = Objects.requireNonNull(fromCommitHash, "fromCommitHash");
        this.toCommitHash = Objects.requireNonNull(toCommitHash, "toCommitHash");
        this.filesChangedCount = filesChangedCount;
        this.newFindingsCount = newFindingsCount;
        this.resolvedFindingsCount = resolvedFindingsCount;
        this.persistentFindingsCount = persistentFindingsCount;
        this.uncertainFindingsCount = uncertainFindingsCount;
        this.factsJson = Objects.requireNonNullElse(factsJson, "{}");
        this.riskSignalsJson = Objects.requireNonNullElse(riskSignalsJson, "[]");
        this.aiSummary = aiSummary;
        this.whatToCheckJson = whatToCheckJson;
        this.testsToRunJson = testsToRunJson;
        this.aiModelName = aiModelName;
        this.aiModelId = aiModelId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getFromCommitHash() {
        return fromCommitHash;
    }

    public String getToCommitHash() {
        return toCommitHash;
    }

    public int getFilesChangedCount() {
        return filesChangedCount;
    }

    public int getNewFindingsCount() {
        return newFindingsCount;
    }

    public int getResolvedFindingsCount() {
        return resolvedFindingsCount;
    }

    public int getPersistentFindingsCount() {
        return persistentFindingsCount;
    }

    public int getUncertainFindingsCount() {
        return uncertainFindingsCount;
    }

    public String getFactsJson() {
        return factsJson;
    }

    public String getRiskSignalsJson() {
        return riskSignalsJson;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public String getWhatToCheckJson() {
        return whatToCheckJson;
    }

    public String getTestsToRunJson() {
        return testsToRunJson;
    }

    public String getAiModelName() {
        return aiModelName;
    }

    public Long getAiModelId() {
        return aiModelId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
