package com.bcosmos.worker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "file_record")
public class WorkerFileRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "relative_path", nullable = false, length = 1000)
    private String relativePath;

    @Column(name = "language", nullable = false, length = 40)
    private String language;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "parse_status", nullable = false, length = 20)
    private String parseStatus;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    protected WorkerFileRecordEntity() {
    }

    public WorkerFileRecordEntity(Long runId, String relativePath, String language, String contentHash,
            int lineCount, String parseStatus, String parseError) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.language = Objects.requireNonNull(language, "language");
        this.contentHash = contentHash;
        this.lineCount = lineCount;
        this.parseStatus = Objects.requireNonNull(parseStatus, "parseStatus");
        this.parseError = parseError;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getLanguage() {
        return language;
    }

    public String getContentHash() {
        return contentHash;
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }
}
