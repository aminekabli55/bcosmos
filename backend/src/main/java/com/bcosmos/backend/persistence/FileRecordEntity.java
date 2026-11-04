package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.ParseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * One file seen by one run (spec v2 section 4.1, table {@code file_record}).
 *
 * <p>{@code relativePath} is repository-relative by construction: no absolute path is stored, returned or logged
 * (spec section 10). {@code parseError} says why a file failed instead of leaving a silent gap.</p>
 */
@Entity
@Table(name = "file_record")
public class FileRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "relative_path", nullable = false, length = 1000)
    private String relativePath;

    @Column(name = "language", nullable = false, length = 40)
    private String language;

    /** Null for a file that was skipped before it was read (size limit, unreadable): no content, no hash. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private ParseStatus parseStatus;

    @Column(name = "parse_error", length = 1000)
    private String parseError;

    /** For JPA only. */
    protected FileRecordEntity() {
    }

    public FileRecordEntity(Long runId, String relativePath, String language, String contentHash, int lineCount,
            ParseStatus parseStatus, String parseError) {
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

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }
}
