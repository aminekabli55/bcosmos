package com.bcosmos.core.model;

/**
 * Indicates whether a target symbol reference was unambiguously linked to a known symbol (Phase 8, spec §3.1 & 4.1).
 */
public enum ResolutionStatus {
    /** Target symbol was found and linked to an exact known symbol in the project. */
    RESOLVED,
    /** Target symbol is external (e.g. JDK/library) or cannot be found in the current run's symbols. */
    UNRESOLVED,
    /** Multiple candidate symbols match the target reference name. */
    AMBIGUOUS
}
