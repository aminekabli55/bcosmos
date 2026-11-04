package com.bcosmos.backend.jobs;

/** Aggregate counts of one run, read from the tables instead of being recomputed in the UI. */
public record AnalysisCounts(long findings, long symbols) {
}
