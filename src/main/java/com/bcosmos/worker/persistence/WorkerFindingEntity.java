package com.bcosmos.worker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "finding")
public class WorkerFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "tool", nullable = false, length = 40)
    private String tool;

    @Column(name = "tool_version", nullable = false, length = 120)
    private String toolVersion;

    @Column(name = "rule_id", nullable = false, length = 200)
    private String ruleId;

    @Column(name = "original_severity", nullable = false, length = 40)
    private String originalSeverity;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "severity_rank", nullable = false)
    private int severityRank;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "start_column", nullable = false)
    private int startColumn;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @Column(name = "end_column", nullable = false)
    private int endColumn;

    @Column(name = "symbol_qualified_name", length = 1000)
    private String symbolQualifiedName;

    @Column(name = "snippet", length = 600)
    private String snippet;

    @Column(name = "normalized_snippet", length = 600)
    private String normalizedSnippet;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "triage_status", nullable = false, length = 20)
    private String triageStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkerFindingEntity() {
    }

    public WorkerFindingEntity(Long runId, Long fileId, String tool, String toolVersion, String ruleId,
            String originalSeverity, String severity, int severityRank, String message, int startLine, int startColumn,
            int endLine, int endColumn, String symbolQualifiedName, String snippet, String normalizedSnippet,
            String fingerprint, String triageStatus, Instant createdAt) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.toolVersion = Objects.requireNonNull(toolVersion, "toolVersion");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.originalSeverity = Objects.requireNonNull(originalSeverity, "originalSeverity");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.severityRank = severityRank;
        this.message = Objects.requireNonNull(message, "message");
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.symbolQualifiedName = symbolQualifiedName;
        this.snippet = snippet;
        this.normalizedSnippet = normalizedSnippet;
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.triageStatus = Objects.requireNonNull(triageStatus, "triageStatus");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getTool() {
        return tool;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getOriginalSeverity() {
        return originalSeverity;
    }

    public String getSeverity() {
        return severity;
    }

    public int getSeverityRank() {
        return severityRank;
    }

    public String getMessage() {
        return message;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public String getSymbolQualifiedName() {
        return symbolQualifiedName;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getNormalizedSnippet() {
        return normalizedSnippet;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getTriageStatus() {
        return triageStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
