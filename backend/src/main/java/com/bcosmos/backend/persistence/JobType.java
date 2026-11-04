package com.bcosmos.backend.persistence;

/** Kind of work a job row describes (spec v2 section 4.1, {@code job.type}). */
public enum JobType {
    /** Full analysis of a registered repository; phase 1 runs it in-process. */
    ANALYSIS
}
