package com.bcosmos.backend.persistence;

/**
 * Triage state of a finding (spec v2 section 4.1, {@code finding.triage_status}).
 *
 * <p>Phase 1 only ever writes {@link #OPEN}: the Quality screen shows findings, triage arrives with the AI Review
 * screen in phase 3.</p>
 */
public enum TriageStatus {
    OPEN,
    ACCEPTED,
    REJECTED,
    DEFERRED
}
