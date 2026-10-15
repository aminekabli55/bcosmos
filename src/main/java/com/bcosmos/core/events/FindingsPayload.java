package com.bcosmos.core.events;

import java.util.List;

/**
 * The findings of one (commit, file, side) (spec v2 section 5.2).
 *
 * <p>{@code side} is {@code BEFORE} for the parent revision and {@code AFTER} for the commit itself: the pair is what
 * lets a later job see a finding disappear between two revisions without re-analysing anything.</p>
 *
 * <p>{@code lineCount} is the line count of the analysed revision. It is the denominator of every per-KLOC figure the
 * Spark jobs produce (spec v2 section 6.1: "findings per rule / severity / repo, per KLOC"), and the event stream is
 * the only channel from the analysers to Spark, so the denominator has to travel with the findings. For a file that
 * could not be read at all it is {@code 0}, which the analytics layer treats as "no code measured", never as
 * "a zero-length file".</p>
 *
 * @param parseStatus {@code OK} or {@code FAILED}; a failed parse is published with an empty finding list rather
 *                    than being dropped, because "the file did not parse" is a fact the dataset needs
 */
public record FindingsPayload(
        String commitHash,
        String parentHash,
        String filePath,
        String contentHash,
        int lineCount,
        Side side,
        String analyzerVersion,
        String rulesetHash,
        String parseStatus,
        List<FindingPayload> findings) {

    /** Which revision of the file these findings belong to. */
    public enum Side {
        BEFORE,
        AFTER
    }

    public FindingsPayload {
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException("commitHash is required");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (filePath.startsWith("/") || filePath.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("filePath must be repository-relative");
        }
        if (side == null) {
            throw new IllegalArgumentException("side is required");
        }
        if (lineCount < 0) {
            throw new IllegalArgumentException("lineCount must not be negative");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
