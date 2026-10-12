package com.bcosmos.backend.jobs;

/**
 * Hands an analysis run to whatever will execute it.
 *
 * <p>Phase 1 has one implementation that runs the analysis in-process. Phase 4 replaces it with a Kafka producer
 * that publishes {@code repo.analysis.requested.v1} and lets {@code analysis-worker} do the work; the caller
 * (the run service) does not change (spec v2 section 12, Phase 1).</p>
 */
public interface JobDispatcher {

    /** Asynchronously executes the run. Never blocks the HTTP request that created it. */
    void dispatch(long analysisRunId);
}
