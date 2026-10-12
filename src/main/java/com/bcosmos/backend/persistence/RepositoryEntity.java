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
     * never logged (spec section 10).
     */
    @Column(name = "canonical_local_path", nullable = false, length = 1000)
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

    public RepositoryEntity(
            String name,
            String canonicalLocalPath,
            RepositorySourceType sourceType,
            String primaryLanguage,
            RepositoryStatus status,
            Instant createdAt) {
        this.name = Objects.requireNonNull(name, "name");
        this.canonicalLocalPath = Objects.requireNonNull(canonicalLocalPath, "canonicalLocalPath");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.primaryLanguage = primaryLanguage;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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
