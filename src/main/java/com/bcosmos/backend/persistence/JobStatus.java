package com.bcosmos.backend.persistence;

/** Lifecycle of one job row (spec v2 section 4.1, {@code job.status}). */
public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
