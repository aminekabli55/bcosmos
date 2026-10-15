package com.bcosmos.backend.jobs;

import com.bcosmos.backend.config.AnalysisConfiguration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Phase 1 {@link JobDispatcher}: runs the analysis in-process on a bounded single-thread executor.
 *
 * <p>Deliberately boring: no Kafka yet (phase 4), no parallel repository analysis, no retries beyond what the run
 * itself records. A rejected submission fails the run immediately with a visible reason instead of leaving it
 * pending forever.</p>
 *
 * <p>It is triggered by {@link AnalysisRunRequested}, not called directly by {@link AnalysisRunService}: the run
 * service decides <em>that</em> work exists, this class decides <em>how</em> it is executed. Phase 4 added
 * {@link KafkaJobDispatcher} as the other implementation of that seam; exactly one of the two exists, chosen by
 * {@code bcosmos.kafka.enabled}, and the run service is untouched by the choice.</p>
 */
@Component
@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class InProcessJobDispatcher implements JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InProcessJobDispatcher.class);

    private final AnalysisRunner runner;
    private final AnalysisRunService runService;
    private final Executor executor;

    public InProcessJobDispatcher(AnalysisRunner runner, AnalysisRunService runService,
            @Qualifier(AnalysisConfiguration.ANALYSIS_EXECUTOR) Executor executor) {
        this.runner = runner;
        this.runService = runService;
        this.executor = executor;
    }

    /** Picks up a committed run and hands it to the executor. */
    @EventListener
    public void onAnalysisRunRequested(AnalysisRunRequested event) {
        dispatch(event.analysisRunId());
    }

    @Override
    public void dispatch(long analysisRunId) {
        try {
            executor.execute(() -> execute(analysisRunId));
        } catch (RejectedExecutionException e) {
            // The queue is full: say so on the run instead of leaving it queued forever.
            runService.fail(analysisRunId, "DISPATCH_REJECTED", "the analysis queue is full, try again later");
            log.warn("analysis run {} was rejected by the executor queue", analysisRunId);
        }
    }

    private void execute(long analysisRunId) {
        try {
            runner.run(analysisRunId);
        } catch (RuntimeException e) {
            // The runner records its own failures; this is the last line of defence for a bug in the runner itself.
            runService.fail(analysisRunId, "DISPATCH_ERROR", e.getClass().getSimpleName());
            log.error("analysis run {} crashed outside the runner", analysisRunId, e);
        }
    }
}
