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

/**
 * One analysis pass over one repository (spec v2 section 4.1, table {@code analysis_run}).
 *
 * <p>The repository is referenced by id, not through a JPA association: the read path of phase 1 is an explicit
 * query per request, which keeps lazy-loading surprises out of the API layer. Associations earn their place with
 * the graph phase.</p>
 */
@Entity
@Table(name = "analysis_run")
public class AnalysisRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "commit_hash", length = 64)
    private String commitHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    private AnalysisStage stage;

    @Column(name = "parser_version", length = 120)
    private String parserVersion;

    @Column(name = "analyzer_version", length = 120)
    private String analyzerVersion;

    @Column(name = "ruleset_hash", length = 64)
    private String rulesetHash;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "files_discovered", nullable = false)
    private int filesDiscovered;

    @Column(name = "files_parsed", nullable = false)
    private int filesParsed;

    @Column(name = "files_failed", nullable = false)
    private int filesFailed;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_summary", length = 500)
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** For JPA only. */
    protected AnalysisRunEntity() {
    }

    public AnalysisRunEntity(Long repositoryId, AnalysisRunStatus status, AnalysisStage stage, int schemaVersion,
            Instant createdAt) {
        this.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId");
        this.status = Objects.requireNonNull(status, "status");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.schemaVersion = schemaVersion;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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

    public AnalysisRunStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisRunStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public AnalysisStage getStage() {
        return stage;
    }

    public void setStage(AnalysisStage stage) {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public void setAnalyzerVersion(String analyzerVersion) {
        this.analyzerVersion = analyzerVersion;
    }

    public String getRulesetHash() {
        return rulesetHash;
    }

    public void setRulesetHash(String rulesetHash) {
        this.rulesetHash = rulesetHash;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public int getFilesDiscovered() {
        return filesDiscovered;
    }

    public void setFilesDiscovered(int filesDiscovered) {
        this.filesDiscovered = filesDiscovered;
    }

    public int getFilesParsed() {
        return filesParsed;
    }

    public void setFilesParsed(int filesParsed) {
        this.filesParsed = filesParsed;
    }

    public int getFilesFailed() {
        return filesFailed;
    }

    public void setFilesFailed(int filesFailed) {
        this.filesFailed = filesFailed;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
