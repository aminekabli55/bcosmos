package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * The text of one analysed file, as the run that analysed it saw it (table {@code file_content}).
 *
 * <p>It exists so Phase 3 can do two things it cannot do otherwise: build a chunk from a symbol's line range
 * without reading the repository from disk (which may have changed since the run), and apply a proposed diff to the
 * exact revision the finding came from. It is a copy inside BCosmos' own database - deleting BCosmos metadata never
 * touches the original repository (spec v2 section 10).</p>
 *
 * <p>The primary key is the file record's id: one file record has exactly one stored revision, and there is no
 * second row to keep consistent.</p>
 */
@Entity
@Table(name = "file_content")
public class FileContentEntity {

    @Id
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "stored_at", nullable = false)
    private Instant storedAt;

    /** For JPA only. */
    protected FileContentEntity() {
    }

    public FileContentEntity(Long fileId, String text, Instant storedAt) {
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        this.text = Objects.requireNonNull(text, "text");
        this.charCount = text.length();
        this.storedAt = Objects.requireNonNull(storedAt, "storedAt");
    }

    public Long getFileId() {
        return fileId;
    }

    public String getText() {
        return text;
    }

    public int getCharCount() {
        return charCount;
    }

    public Instant getStoredAt() {
        return storedAt;
    }
}
