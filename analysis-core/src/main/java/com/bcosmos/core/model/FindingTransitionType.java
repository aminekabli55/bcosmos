package com.bcosmos.core.model;

/**
 * Finding transition classification between two analysis snapshots (Phase 8, spec v2 section 3.3 & 4.2).
 */
public enum FindingTransitionType {
    /** Finding was newly introduced in the target snapshot. */
    NEW,
    /** Finding was resolved in the target snapshot (target file parsed cleanly and rule is active). */
    RESOLVED,
    /** Finding persists across both snapshots with identical fingerprint. */
    PERSISTENT,
    /** Resolution cannot be proven because the file failed to parse or rule was disabled in target run. */
    UNCERTAIN
}
