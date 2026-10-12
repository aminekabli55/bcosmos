package com.bcosmos.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything the local-analysis flow is allowed to do (spec v2 section 10: canonicalize paths, restrict to
 * user-approved roots, limit file size and count).
 *
 * <p>Defaults are safe for a laptop and are overridable per profile: {@code application.yml} ships developer
 * defaults, {@code application-test.yml} points the allowed roots at the fixture, and {@code application-prod.yml}
 * requires the roots to be set explicitly.</p>
 *
 * @param repository repository registration and discovery settings
 */
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

        /** {@code limits} falls back to the built-in defaults when the profile does not configure any. */
        public Repository {
            allowedRoots = allowedRoots == null ? List.of() : List.copyOf(allowedRoots);
            limits = limits == null ? new Limits(0, 0) : limits;
            ignoredDirectories = ignoredDirectories == null ? List.of() : List.copyOf(ignoredDirectories);
        }
    }

    /**
     * @param maxFiles         files per run
     * @param maxFileSizeBytes files larger than this are skipped with a recorded reason
     */
    public record Limits(int maxFiles, long maxFileSizeBytes) {

        private static final int DEFAULT_MAX_FILES = 20_000;
        private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 2_000_000L;

        public Limits {
            maxFiles = maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
            maxFileSizeBytes = maxFileSizeBytes <= 0 ? DEFAULT_MAX_FILE_SIZE_BYTES : maxFileSizeBytes;
        }
    }
}
