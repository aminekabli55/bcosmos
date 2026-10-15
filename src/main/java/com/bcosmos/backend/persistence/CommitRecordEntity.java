package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One commit the miner discovered (spec v2 section 4.1, table {@code commit_record}).
 *
 * <p>{@code authorHash} is a hash, never an email or a name: the corpus carries no personal data (spec section 10).
 * {@code parentHashes} is a Postgres {@code text[]} because a merge commit has more than one parent.</p>
 */
@Entity
@Table(name = "commit_record")
public class CommitRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "parent_hashes", columnDefinition = "text[]")
    private String[] parentHashes = new String[0];

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt;

    @Column(name = "author_hash", length = 64)
    private String authorHash;

    @Column(name = "changed_java_files", nullable = false)
    private int changedJavaFiles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected CommitRecordEntity() {
    }

    public CommitRecordEntity(Long repositoryId, String hash, String[] parentHashes, Instant committedAt,
            String authorHash, int changedJavaFiles, Instant createdAt) {
        this.repositoryId = Objects.requireNonNull(repositoryId, "repositoryId");
        this.hash = Objects.requireNonNull(hash, "hash");
        this.parentHashes = parentHashes == null ? new String[0] : parentHashes.clone();
        this.committedAt = Objects.requireNonNull(committedAt, "committedAt");
        this.authorHash = authorHash;
        this.changedJavaFiles = changedJavaFiles;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getHash() {
        return hash;
    }

    public String[] getParentHashes() {
        return parentHashes.clone();
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public String getAuthorHash() {
        return authorHash;
    }

    public int getChangedJavaFiles() {
        return changedJavaFiles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Applies a re-delivered event to an existing row: at-least-once delivery must not create a second row. */
    public void updateFrom(String[] parentHashes, Instant committedAt, String authorHash, int changedJavaFiles) {
        this.parentHashes = parentHashes == null ? new String[0] : parentHashes.clone();
        this.committedAt = committedAt;
        this.authorHash = authorHash;
        this.changedJavaFiles = changedJavaFiles;
    }

    @Override
    public String toString() {
        return "CommitRecordEntity{id=" + id + ", repositoryId=" + repositoryId + ", hash=" + hash
                + ", parentHashes=" + Arrays.toString(parentHashes) + ", changedJavaFiles=" + changedJavaFiles + "}";
    }
}
