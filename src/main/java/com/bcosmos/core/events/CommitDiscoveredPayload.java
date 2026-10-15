package com.bcosmos.core.events;

import java.time.Instant;
import java.util.List;

/**
 * One commit the miner wants analysed (spec v2 section 5.2).
 *
 * <p>Only commits whose Java files changed are published; the changed-file list travels with the event so the worker
 * does not have to diff the commit itself before it can decide what to read.</p>
 *
 * @param changedJavaFiles repository-relative paths of the {@code .java} files that changed, never empty
 */
public record CommitDiscoveredPayload(
        String commitHash,
        List<String> parentHashes,
        Instant committedAt,
        List<String> changedJavaFiles) {

    public CommitDiscoveredPayload {
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException("commitHash is required");
        }
        parentHashes = parentHashes == null ? List.of() : List.copyOf(parentHashes);
        changedJavaFiles = changedJavaFiles == null ? List.of() : List.copyOf(changedJavaFiles);
        if (changedJavaFiles.isEmpty()) {
            throw new IllegalArgumentException("a discovered commit must have at least one changed Java file");
        }
    }
}
