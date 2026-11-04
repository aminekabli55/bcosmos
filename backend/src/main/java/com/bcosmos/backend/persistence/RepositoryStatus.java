package com.bcosmos.backend.persistence;

/** Status of a registered repository (spec v2 section 4.1, {@code repository.status}). */
public enum RepositoryStatus {
    /** Registered and validated, but never analysed successfully yet. */
    REGISTERED,
    /** At least one analysis run finished successfully. */
    ANALYSED,
    /** The last analysis run failed. */
    FAILED
}
