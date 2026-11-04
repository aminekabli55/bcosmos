package com.bcosmos.core.model;

/**
 * Result of parsing one file.
 *
 * <p>{@link #FAILED} means the parser reported at least one problem. A failed file is never silently skipped:
 * the run counts it and shows why (spec v2 section 12, Phase 1 "parse failures are counted ... never hidden").</p>
 */
public enum ParseStatus {
    OK,
    FAILED
}
