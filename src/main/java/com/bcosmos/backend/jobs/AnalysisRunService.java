package com.bcosmos.backend.jobs;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.JobEntity;
import com.bcosmos.backend.persistence.JobJpaRepository;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.JobStatus;
import com.bcosmos.backend.persistence.JobType;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.repository.DiscoveryResult;
import com.bcosmos.backend.repository.RepositoryNotFoundException;
import com.bcosmos.core.language.LanguageAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creates runs, owns their status transitions and reads their numbers.
 *
 * <p>A run is always paired with a {@link JobEntity}: phase 1 executes the job in-process, but the row already
 * carries the attempt, the stage and the error summary the API reports, which is the same shape phase 4 needs when
 * a worker picks the job up from Kafka.</p>
 *
 * <p>The service never calls a {@link JobDispatcher} directly: it publishes {@link AnalysisRunRequested} once the
 * creating transaction has committed, so the executor can always see the rows it is asked to process. That keeps
 * the bean graph acyclic and lets phase 4 replace the in-process listener with a Kafka publisher (spec v2 section
 * 12, Phase 1).</p>
 */
@Service
public class AnalysisRunService {

    /** Bumped when the persisted shape of a run changes, so two runs stay comparable (ADR 0010). */
    public static final int SCHEMA_VERSION = 1;

    /** Recorded when discovery hit the configured file limit; the run still succeeds, but says so. */
    public static final String FILE_LIMIT_REACHED = "FILE_LIMIT_REACHED";

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AnalysisRunJpaRepository runs;
    private final JobJpaRepository jobs;
    private final RepositoryJpaRepository repositories;
    private final FindingJpaRepository findings;
    private final SymbolJpaRepository symbols;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AnalysisRunService(AnalysisRunJpaRepository runs, JobJpaRepository jobs,
            RepositoryJpaRepository repositories, FindingJpaRepository findings,
            SymbolJpaRepository symbols, ApplicationEventPublisher events, Clock clock) {
        this.runs = runs;
        this.jobs = jobs;
        this.repositories = repositories;
        this.findings = findings;
        this.symbols = symbols;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates the run and its job and dispatches the work once the transaction has committed, so the worker can
     * always see the rows it is asked to process.
     */
    @Transactional
    public AnalysisRunEntity start(long repositoryId) {
        RepositoryEntity repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));
        Instant now = clock.instant();
        AnalysisRunEntity run = runs.save(new AnalysisRunEntity(repository.getId(), AnalysisRunStatus.PENDING,
                AnalysisStage.QUEUED, SCHEMA_VERSION, now));
        jobs.save(new JobEntity(run.getId(), JobType.ANALYSIS, JobStatus.PENDING, 1, now));

        long runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                events.publishEvent(new AnalysisRunRequested(runId));
            }
        });
        return run;
    }

    /** Marks the run and its job as running. */
    @Transactional
    public AnalysisRunEntity markRunning(long runId) {
        AnalysisRunEntity run = require(runId);
        Instant now = clock.instant();
        run.setStatus(AnalysisRunStatus.RUNNING);
        run.setStage(AnalysisStage.DISCOVERY);
        run.setStartedAt(now);
        jobOf(runId).ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(now);
        });
        return runs.save(run);
    }

    /** Records which tools produced the results (parser, analyzer, rule set). */
    @Transactional
    public void recordTools(long runId, LanguageAdapter<?> adapter) {
        AnalysisRunEntity run = require(runId);
        run.setParserVersion(adapter.parserVersion());
        run.setAnalyzerVersion(adapter.analyzerVersion());
        run.setRulesetHash(adapter.defaultRuleSet().hash());
        runs.save(run);
    }

    /** Records what discovery found, and whether it had to stop early. */
    @Transactional
    public void recordDiscovery(long runId, DiscoveryResult discovery) {
        AnalysisRunEntity run = require(runId);
        run.setFilesDiscovered(discovery.discoveredCount());
        run.setStage(AnalysisStage.PARSING);
        if (discovery.fileLimitReached()) {
            run.setErrorCode(FILE_LIMIT_REACHED);
            run.setErrorSummary("discovery stopped at the configured file limit, so the repository is only "
                    + "partially analysed");
        }
        runs.save(run);
    }

    @Transactional
    public void recordStage(long runId, AnalysisStage stage) {
        AnalysisRunEntity run = require(runId);
        run.setStage(stage);
        runs.save(run);
    }

    /** Completes the run successfully and updates the repository status. */
    @Transactional
    public void finish(long runId, int filesParsed, int filesFailed) {
        AnalysisRunEntity run = require(runId);
        Instant now = clock.instant();
        run.setStatus(AnalysisRunStatus.SUCCEEDED);
        run.setStage(AnalysisStage.COMPLETED);
        run.setFilesParsed(filesParsed);
        run.setFilesFailed(filesFailed);
        run.setFinishedAt(now);
        runs.save(run);
        jobOf(runId).ifPresent(job -> {
            job.setStatus(JobStatus.SUCCEEDED);
            job.setFinishedAt(now);
        });
        updateRepositoryStatus(run.getRepositoryId(), RepositoryStatus.ANALYSED);
    }

    /** Fails the run with a code and a summary the UI can show. */
    @Transactional
    public void fail(long runId, String errorCode, String errorSummary) {
        AnalysisRunEntity run = require(runId);
        Instant now = clock.instant();
        run.setStatus(AnalysisRunStatus.FAILED);
        run.setStage(AnalysisStage.COMPLETED);
        run.setErrorCode(errorCode);
        run.setErrorSummary(summarise(errorSummary));
        run.setFinishedAt(now);
        runs.save(run);
        jobOf(runId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorSummary(summarise(errorSummary));
            job.setFinishedAt(now);
        });
        updateRepositoryStatus(run.getRepositoryId(), RepositoryStatus.FAILED);
    }

    @Transactional(readOnly = true)
    public AnalysisRunEntity require(long runId) {
        return runs.findById(runId).orElseThrow(() -> new AnalysisRunNotFoundException(runId));
    }

    @Transactional(readOnly = true)
    public AnalysisCounts counts(long runId) {
        return new AnalysisCounts(findings.countByRunId(runId), symbols.countByRunId(runId));
    }

    @Transactional(readOnly = true)
    public Optional<JobEntity> jobOf(long runId) {
        return jobs.findFirstByRunIdOrderByIdDesc(runId);
    }

    @Transactional(readOnly = true)
    public Page<AnalysisRunEntity> listByRepository(long repositoryId, Pageable pageable) {
        if (!repositories.existsById(repositoryId)) {
            throw new RepositoryNotFoundException(repositoryId);
        }
        return runs.findByRepositoryIdOrderByCreatedAtDesc(repositoryId, pageable);
    }

    private void updateRepositoryStatus(Long repositoryId, RepositoryStatus status) {
        repositories.findById(repositoryId).ifPresent(repository -> {
            repository.setStatus(status);
            repositories.save(repository);
        });
    }

    private static String summarise(String summary) {
        if (summary == null || summary.length() <= MAX_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_LENGTH);
    }
}
