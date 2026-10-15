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

/** A registered repository (spec v2 section 4.1, table {@code repository}). */
@Entity
@Table(name = "repository")
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Private metadata: the canonical path is used to read files, never returned by the API (spec section 8) and
     * never logged (spec section 10). Null for a mined repository, which lives in the miner's own cache: the
     * backend does not own that path and must not invent one (changelog 0016).
     */
    @Column(name = "canonical_local_path", length = 1000)
    private String canonicalLocalPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private RepositorySourceType sourceType;

    @Column(name = "primary_language", length = 40)
    private String primaryLanguage;

    @Column(name = "remote_url", length = 1000)
    private String remoteUrl;

    @Column(name = "default_branch", length = 200)
    private String defaultBranch;

    @Column(name = "license_spdx", length = 80)
    private String licenseSpdx;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RepositoryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected RepositoryEntity() {
    }

    /**
     * A repository registered from a local path. The path is required here rather than in the field: the database
     * check constraint allows {@code NULL} for a mined row, and this constructor is the local case.
     */
    public RepositoryEntity(
            String name,
            String canonicalLocalPath,
            RepositorySourceType sourceType,
            String primaryLanguage,
            RepositoryStatus status,
            Instant createdAt) {
        this.name = Objects.requireNonNull(name, "name");
        this.canonicalLocalPath = canonicalLocalPath;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        if (sourceType == RepositorySourceType.LOCAL) {
            // Mirrors the ck_repository_source constraint, so a bad row fails here rather than in Postgres.
            Objects.requireNonNull(canonicalLocalPath, "a local repository needs a canonical path");
        }
        this.primaryLanguage = primaryLanguage;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * A repository the miner clones, identified by its remote URL. It has no local path of its own: the clone lives
     * in the miner's cache, which this process never reads.
     */
    public static RepositoryEntity mined(String name, String remoteUrl, String defaultBranch, String licenseSpdx,
            Instant createdAt) {
        RepositoryEntity repository = new RepositoryEntity(name, null, RepositorySourceType.MINED, "java",
                RepositoryStatus.REGISTERED, createdAt);
        repository.remoteUrl = Objects.requireNonNull(remoteUrl, "remoteUrl");
        repository.defaultBranch = defaultBranch;
        repository.licenseSpdx = licenseSpdx;
        return repository;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCanonicalLocalPath() {
        return canonicalLocalPath;
    }

    public RepositorySourceType getSourceType() {
        return sourceType;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    /**
     * Set from the files a finished run analysed ({@code PrimaryLanguageResolver}, ADR 0033): registration cannot
     * know the language of a repository it never reads.
     */
    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getLicenseSpdx() {
        return licenseSpdx;
    }

    public void setLicenseSpdx(String licenseSpdx) {
        this.licenseSpdx = licenseSpdx;
    }

    public RepositoryStatus getStatus() {
        return status;
    }

    public void setStatus(RepositoryStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
