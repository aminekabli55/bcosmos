package com.bcosmos.backend.persistence;

/** Lifecycle of one analysis run (spec v2 section 4.1, {@code analysis_run.status}). */
public enum AnalysisRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean finished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
