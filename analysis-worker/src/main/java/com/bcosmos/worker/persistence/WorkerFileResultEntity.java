package com.bcosmos.worker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Idempotency record for files analysed by the worker (spec v2 section 5.3, ADR 0027).
 */
@Entity
@Table(name = "worker_file_result")
public class WorkerFileResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "commit_hash", nullable = false, length = 64)
    private String commitHash;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "side", nullable = false, length = 16)
    private String side;

    @Column(name = "analyzer_version", nullable = false, length = 120)
    private String analyzerVersion;

    @Column(name = "ruleset_hash", nullable = false, length = 64)
    private String rulesetHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected WorkerFileResultEntity() {
    }

    public WorkerFileResultEntity(Long repositoryId, String commitHash, String filePath, String side,
            String analyzerVersion, String rulesetHash, String status, int findingCount, Instant processedAt) {
        this.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId");
        this.commitHash = Objects.requireNonNull(commitHash, "commitHash");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.side = Objects.requireNonNull(side, "side");
        this.analyzerVersion = Objects.requireNonNull(analyzerVersion, "analyzerVersion");
        this.rulesetHash = Objects.requireNonNull(rulesetHash, "rulesetHash");
        this.status = Objects.requireNonNull(status, "status");
        this.findingCount = findingCount;
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSide() {
        return side;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public String getRulesetHash() {
        return rulesetHash;
    }

    public String getStatus() {
        return status;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setFindingCount(int findingCount) {
        this.findingCount = findingCount;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
