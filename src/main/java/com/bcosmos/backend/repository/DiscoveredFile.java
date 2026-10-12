package com.bcosmos.backend.repository;

import java.nio.file.Path;

/**
 * A file file discovery decided to analyse.
 *
 * @param relativePath repository-relative path with {@code /} separators; the only path that reaches the database,
 *                     the API or the logs
 * @param absolutePath canonical path used to read the bytes; never stored or returned
 * @param sizeBytes    size on disk when it was discovered
 */
public record DiscoveredFile(Path relativePath, Path absolutePath, long sizeBytes) {

    /** Relative path as a {@code /}-separated string, which is what {@code analysis-core} expects. */
    public String relativePathString() {
        return relativePath.toString().replace('\\', '/');
    }
}
