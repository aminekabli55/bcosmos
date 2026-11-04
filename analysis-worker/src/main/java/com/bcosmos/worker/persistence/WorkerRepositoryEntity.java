package com.bcosmos.worker.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "repository")
public class WorkerRepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "canonical_local_path", length = 1000)
    private String canonicalLocalPath;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "primary_language", length = 40)
    private String primaryLanguage;

    @Column(name = "remote_url", length = 1000)
    private String remoteUrl;

    @Column(name = "default_branch", length = 200)
    private String defaultBranch;

    @Column(name = "license_spdx", length = 80)
    private String licenseSpdx;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkerRepositoryEntity() {
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

    public String getSourceType() {
        return sourceType;
    }

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getLicenseSpdx() {
        return licenseSpdx;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
