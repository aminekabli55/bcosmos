package com.bcosmos.backend.repository;

/**
 * A candidate file that was not analysed, with the reason.
 *
 * <p>Skipped files are recorded as failed files on the run instead of disappearing: Phase 1 requires that
 * discovery failures are countable and visible (spec v2 section 12).</p>
 *
 * @param relativePath repository-relative path with {@code /} separators
 * @param reason       short, path-free explanation safe to show in the UI
 */
public record SkippedFile(String relativePath, String reason) {
}
