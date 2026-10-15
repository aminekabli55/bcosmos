package com.bcosmos.worker.persistence;

import com.bcosmos.core.model.Finding;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles idempotent persistence for the analysis worker (spec v2 section 5.3, ADR 0027).
 */
@Service
public class WorkerPersistenceService {

    private final WorkerAnalysisRunJpaRepository runs;
    private final WorkerFileRecordJpaRepository fileRecords;
    private final WorkerFindingJpaRepository findings;
    private final WorkerCommitRecordJpaRepository commitRecords;
    private final WorkerFileResultJpaRepository fileResults;
    private final Clock clock;

    public WorkerPersistenceService(
            WorkerAnalysisRunJpaRepository runs,
            WorkerFileRecordJpaRepository fileRecords,
            WorkerFindingJpaRepository findings,
            WorkerCommitRecordJpaRepository commitRecords,
            WorkerFileResultJpaRepository fileResults,
            Clock clock) {
        this.runs = runs;
        this.fileRecords = fileRecords;
        this.findings = findings;
        this.commitRecords = commitRecords;
        this.fileResults = fileResults;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<WorkerAnalysisRunEntity> findRun(long runId) {
        return runs.findById(runId);
    }

    @Transactional
    public void startRun(long runId, int filesDiscovered) {
        runs.findById(runId).ifPresent(run -> {
            run.setStatus("RUNNING");
            run.setStage("PARSING");
            run.setFilesDiscovered(filesDiscovered);
            run.setStartedAt(clock.instant());
            runs.save(run);
        });
    }

    @Transactional
    public void completeRun(long runId, int filesParsed, int filesFailed, String parserVersion,
            String analyzerVersion, String rulesetHash) {
        runs.findById(runId).ifPresent(run -> {
            run.setStatus("SUCCEEDED");
            run.setStage("COMPLETED");
            run.setFilesParsed(filesParsed);
            run.setFilesFailed(filesFailed);
            run.setParserVersion(parserVersion);
            run.setAnalyzerVersion(analyzerVersion);
            run.setRulesetHash(rulesetHash);
            run.setFinishedAt(clock.instant());
            runs.save(run);
        });
    }

    @Transactional
    public void failRun(long runId, String errorCode, String errorSummary) {
        runs.findById(runId).ifPresent(run -> {
            run.setStatus("FAILED");
            run.setStage("FAILED");
            run.setErrorCode(errorCode);
            run.setErrorSummary(truncate(errorSummary, 500));
            run.setFinishedAt(clock.instant());
            runs.save(run);
        });
    }

    @Transactional
    public void saveFileAnalysis(long runId, String relativePath, String language, String contentHash,
            int lineCount, String parseStatus, String parseError, List<Finding> findingList) {
        // Upsert file record: if already exists for this run and path, re-use its ID and clear old findings
        WorkerFileRecordEntity record = fileRecords.findByRunIdAndRelativePath(runId, relativePath)
                .orElse(null);

        if (record != null) {
            findings.deleteByFileId(record.getId());
        } else {
            record = fileRecords.save(new WorkerFileRecordEntity(
                    runId, relativePath, language, contentHash, lineCount, parseStatus, truncate(parseError, 1000)));
        }

        Long fileId = record.getId();
        Instant now = clock.instant();

        if (findingList != null && !findingList.isEmpty()) {
            List<WorkerFindingEntity> entities = findingList.stream()
                    .map(f -> toFindingEntity(runId, fileId, f, now))
                    .toList();
            findings.saveAll(entities);
        }
    }

    @Transactional(readOnly = true)
    public boolean isFileResultProcessed(Long repositoryId, String commitHash, String filePath, String side,
            String analyzerVersion, String rulesetHash) {
        return fileResults.findByRepositoryIdAndCommitHashAndFilePathAndSideAndAnalyzerVersionAndRulesetHash(
                repositoryId, commitHash, filePath, side, analyzerVersion, rulesetHash).isPresent();
    }

    @Transactional
    public void recordFileResult(Long repositoryId, String commitHash, String filePath, String side,
            String analyzerVersion, String rulesetHash, String status, int findingCount) {
        Optional<WorkerFileResultEntity> existing = fileResults
                .findByRepositoryIdAndCommitHashAndFilePathAndSideAndAnalyzerVersionAndRulesetHash(
                        repositoryId, commitHash, filePath, side, analyzerVersion, rulesetHash);

        if (existing.isPresent()) {
            WorkerFileResultEntity entity = existing.get();
            entity.setStatus(status);
            entity.setFindingCount(findingCount);
            entity.setProcessedAt(clock.instant());
            fileResults.save(entity);
        } else {
            fileResults.save(new WorkerFileResultEntity(
                    repositoryId, commitHash, filePath, side, analyzerVersion, rulesetHash,
                    status, findingCount, clock.instant()));
        }
    }

    @Transactional
    public void saveCommitRecord(Long repositoryId, String commitHash, List<String> parentHashes,
            Instant committedAt, int changedFiles) {
        if (commitRecords.findByRepositoryIdAndHash(repositoryId, commitHash).isPresent()) {
            return;
        }
        String[] parents = parentHashes == null ? new String[0] : parentHashes.toArray(new String[0]);
        commitRecords.save(new WorkerCommitRecordEntity(
                repositoryId, commitHash, parents, committedAt, null, changedFiles, clock.instant()));
    }

    private static WorkerFindingEntity toFindingEntity(long runId, long fileId, Finding finding, Instant createdAt) {
        return new WorkerFindingEntity(
                runId,
                fileId,
                truncate(finding.tool(), 40),
                truncate(finding.toolVersion(), 120),
                truncate(finding.ruleId(), 200),
                truncate(finding.originalSeverity(), 40),
                finding.severity().name(),
                finding.severity().ordinal(),
                truncate(finding.message(), 1000),
                finding.span().startLine(),
                finding.span().startColumn(),
                finding.span().endLine(),
                finding.span().endColumn(),
                truncate(finding.symbolQualifiedName(), 1000),
                truncate(finding.snippet(), 600),
                truncate(finding.normalizedSnippet(), 600),
                finding.fingerprint(),
                "OPEN",
                createdAt);
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
