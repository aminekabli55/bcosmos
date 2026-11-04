package com.bcosmos.backend.repository;

import java.util.List;

/**
 * Outcome of walking a repository.
 *
 * @param files            files to analyse, ordered by relative path so a run is reproducible
 * @param skipped          candidates that were rejected (too large, unreadable), each with a reason
 * @param fileLimitReached true when discovery stopped because the configured file limit was hit; the run records
 *                         this instead of hiding the truncated result
 */
public record DiscoveryResult(List<DiscoveredFile> files, List<SkippedFile> skipped, boolean fileLimitReached) {

    public DiscoveryResult {
        files = List.copyOf(files);
        skipped = List.copyOf(skipped);
    }

    public int discoveredCount() {
        return files.size();
    }

    public int skippedCount() {
        return skipped.size();
    }
}
