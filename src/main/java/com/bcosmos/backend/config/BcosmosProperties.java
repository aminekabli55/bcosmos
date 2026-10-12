package com.bcosmos.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "bcosmos")
public record BcosmosProperties(Repository repository) {

    public BcosmosProperties {
        repository = repository == null ? new Repository(null, null, null) : repository;
    }

    /**
     * @param allowedRoots       roots a repository path must live under; empty means nothing can be registered
     * @param limits             discovery limits
     * @param ignoredDirectories directories never walked; empty means {@code RepositoryIgnoreRules}'s defaults
     */
    public record Repository(List<String> allowedRoots, Limits limits, List<String> ignoredDirectories) {

        public Repository {
            allowedRoots = allowedRoots == null ? List.of() : List.copyOf(allowedRoots);
            limits = limits == null ? new Limits(0, 0) : limits;
            ignoredDirectories = ignoredDirectories == null ? List.of() : List.copyOf(ignoredDirectories);
        }
    }

    public record Limits(int maxFiles, long maxFileSizeBytes) {

        private static final int DEFAULT_MAX_FILES = 20_000;
        private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 2_000_000L;

        public Limits {
            maxFiles = maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
            maxFileSizeBytes = maxFileSizeBytes <= 0 ? DEFAULT_MAX_FILE_SIZE_BYTES : maxFileSizeBytes;
        }
    }
}
