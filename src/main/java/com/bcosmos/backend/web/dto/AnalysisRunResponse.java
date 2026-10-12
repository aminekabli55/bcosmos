package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.jobs.AnalysisCounts;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.JobEntity;
import com.bcosmos.backend.persistence.JobStatus;
import java.time.Instant;

/**
 * Status of one analysis run (spec v2 section 8, {@code GET /api/analyses/{id}}).
 *
 * <p>The counters are the honest progress report: discovered, parsed and failed files plus how many findings and
 * symbols were stored. While a run is in progress they reflect what has actually happened, not an estimate.</p>
 */
public record AnalysisRunResponse(
        Long id,
        Long repositoryId,
        AnalysisRunStatus status,
        AnalysisStage stage,
        String commitHash,
        String parserVersion,
        String analyzerVersion,
        String rulesetHash,
        int schemaVersion,
        int filesDiscovered,
        int filesParsed,
        int filesFailed,
        long findingsCount,
        long symbolsCount,
        String errorCode,
        String errorSummary,
        JobStatus jobStatus,
        String jobErrorSummary,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static AnalysisRunResponse of(AnalysisRunEntity run, AnalysisCounts counts, JobEntity job) {
        return new AnalysisRunResponse(
                run.getId(),
                run.getRepositoryId(),
                run.getStatus(),
                run.getStage(),
                run.getCommitHash(),
                run.getParserVersion(),
                run.getAnalyzerVersion(),
                run.getRulesetHash(),
                run.getSchemaVersion(),
                run.getFilesDiscovered(),
                run.getFilesParsed(),
                run.getFilesFailed(),
                counts == null ? 0 : counts.findings(),
                counts == null ? 0 : counts.symbols(),
                run.getErrorCode(),
                run.getErrorSummary(),
                job == null ? null : job.getStatus(),
                job == null ? null : job.getErrorSummary(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
