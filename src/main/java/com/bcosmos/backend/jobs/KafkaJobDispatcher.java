package com.bcosmos.backend.jobs;

import com.bcosmos.backend.events.EventPublisher;
import com.bcosmos.backend.events.PipelineException;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.core.events.AnalysisRequestedPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Phase 4 {@link JobDispatcher}: publishes {@code repo.analysis.requested.v1} and lets {@code analysis-worker} run
 * the analysis (spec v2 section 5.1).
 *
 * <p>It is the same seam as {@link InProcessJobDispatcher} — an {@link AnalysisRunRequested} listener — so swapping
 * the two changes nothing above it. The event carries the local path, which the worker needs to read the files and
 * which therefore travels on the broker but is never returned by the API or logged (spec v2 section 10).</p>
 *
 * <p>The partition key is the repository id, so the commits and runs of one repository stay ordered relative to each
 * other while different repositories are analysed in parallel.</p>
 */
@Component
@ConditionalOnProperty(name = "bcosmos.kafka.enabled", havingValue = "true")
public class KafkaJobDispatcher implements JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaJobDispatcher.class);

    private final AnalysisRunService runService;
    private final RepositoryJpaRepository repositories;
    private final EventPublisher events;

    public KafkaJobDispatcher(AnalysisRunService runService, RepositoryJpaRepository repositories,
            EventPublisher events) {
        this.runService = runService;
        this.repositories = repositories;
        this.events = events;
    }

    @EventListener
    public void onAnalysisRunRequested(AnalysisRunRequested event) {
        dispatch(event.analysisRunId());
    }

    @Override
    public void dispatch(long analysisRunId) {
        try {
            publish(analysisRunId);
            log.info("analysis run {} was published to {}", analysisRunId, KafkaTopics.ANALYSIS_REQUESTED);
        } catch (PipelineException e) {
            // Nothing was handed to a worker, so the run cannot stay PENDING: say why and leave it failed.
            runService.fail(analysisRunId, "DISPATCH_FAILED", e.getMessage());
            log.warn("analysis run {} could not be published: {}", analysisRunId, e.getMessage());
        } catch (RuntimeException e) {
            runService.fail(analysisRunId, "DISPATCH_ERROR", e.getClass().getSimpleName());
            log.error("analysis run {} crashed while being published", analysisRunId, e);
        }
    }

    /**
     * Reads what the worker needs and publishes the request. It changes no run state itself: the run service and the
     * repositories own their own transactions, and reading two rows does not need one of its own.
     */
    void publish(long analysisRunId) {
        var run = runService.require(analysisRunId);
        RepositoryEntity repository = repositories.findById(run.getRepositoryId())
                .orElseThrow(() -> new PipelineException("analysis run " + analysisRunId + " has no repository"));
        AnalysisRequestedPayload payload = new AnalysisRequestedPayload(analysisRunId,
                repository.getCanonicalLocalPath(), repository.getPrimaryLanguage());
        events.publish(KafkaTopics.ANALYSIS_REQUESTED, String.valueOf(repository.getId()),
                EventEnvelope.of(EventType.ANALYSIS_REQUESTED, repository.getId(), null, payload));
    }
}
