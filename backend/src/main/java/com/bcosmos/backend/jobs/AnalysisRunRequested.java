package com.bcosmos.backend.jobs;

/**
 * Published after a run and its job row have been committed, asking whoever executes analyses to pick it up.
 *
 * <p>The event exists to keep the wiring acyclic: {@link AnalysisRunService} creates runs, {@link AnalysisRunner}
 * executes them and {@link InProcessJobDispatcher} connects the two. Injecting the dispatcher into the run service
 * would close the loop dispatcher → runner → run service. Publishing an event instead keeps the run service
 * depending only on the Spring context, and it is exactly the seam phase 4 needs: the Kafka implementation
 * subscribes to the same event and publishes {@code repo.analysis.requested.v1} without the run service changing
 * (ADR 0003, spec v2 section 12, Phase 1).</p>
 *
 * @param analysisRunId the run that has been committed and should start
 */
public record AnalysisRunRequested(long analysisRunId) {
}
