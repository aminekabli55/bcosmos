package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
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
 * A normalized finding stored by one run (spec v2 section 4.1, table {@code finding}).
 *
 * <p>{@code severityRank} is stored next to the severity name so the database can order findings by severity:
 * ordering by the string would put CRITICAL before HIGH but also INFO before LOW, which reads well by accident and
 * breaks the moment a value changes.</p>
 */
@Entity
@Table(name = "finding")
public class FindingEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "triage_status", nullable = false, length = 20)
    private TriageStatus triageStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected FindingEntity() {
    }

    public FindingEntity(Long runId, Long fileId, String tool, String toolVersion, String ruleId,
            String originalSeverity, Severity severity, String message, Span span, String symbolQualifiedName,
            String snippet, String normalizedSnippet, String fingerprint, TriageStatus triageStatus,
            Instant createdAt) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.toolVersion = Objects.requireNonNull(toolVersion, "toolVersion");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.originalSeverity = Objects.requireNonNull(originalSeverity, "originalSeverity");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.severityRank = severity.ordinal();
        this.message = Objects.requireNonNull(message, "message");
        this.startLine = span.startLine();
        this.startColumn = span.startColumn();
        this.endLine = span.endLine();
        this.endColumn = span.endColumn();
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

    public Severity getSeverity() {
        return severity;
    }

    public int getSeverityRank() {
        return severityRank;
    }

    public String getMessage() {
        return message;
    }

    public Span getSpan() {
        return new Span(startLine, startColumn, endLine, endColumn);
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

    public TriageStatus getTriageStatus() {
        return triageStatus;
    }

    /**
     * Records a triage decision.
     *
     * <p>Phase 3's review endpoint moves a finding's triage state, because accepting or rejecting a suggestion is
     * exactly the decision the state tracks (spec v2 sections 8 and 9). Nothing else writes this field.</p>
     */
    public void triage(TriageStatus decision) {
        this.triageStatus = Objects.requireNonNull(decision, "decision");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
