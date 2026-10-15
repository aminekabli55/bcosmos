package com.bcosmos.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.worker.persistence.WorkerCommitRecordJpaRepository;
import com.bcosmos.worker.persistence.WorkerFileRecordEntity;
import com.bcosmos.worker.persistence.WorkerFileRecordJpaRepository;
import com.bcosmos.worker.persistence.WorkerFileResultEntity;
import com.bcosmos.worker.persistence.WorkerFileResultJpaRepository;
import com.bcosmos.worker.persistence.WorkerFindingJpaRepository;
import com.bcosmos.worker.persistence.WorkerAnalysisRunJpaRepository;
import com.bcosmos.worker.persistence.WorkerPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Duplicate delivery idempotency tests (spec v2 section 5.3, ADR 0027)")
class DuplicateDeliveryTest {

    private WorkerAnalysisRunJpaRepository runs = mock();
    private WorkerFileRecordJpaRepository fileRecords = mock();
    private WorkerFindingJpaRepository findings = mock();
    private WorkerCommitRecordJpaRepository commitRecords = mock();
    private WorkerFileResultJpaRepository fileResults = mock();
    private Clock clock = Clock.systemUTC();

    private WorkerPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new WorkerPersistenceService(runs, fileRecords, findings, commitRecords, fileResults, clock);
    }

    @Test
    void savingFileAnalysisTwiceCleansOldFindingsForThatFile() {
        long runId = 10L;
        String path = "src/Foo.java";

        WorkerFileRecordEntity existingRecord = new WorkerFileRecordEntity(
                runId, path, "java", "hash1", 20, "OK", null);
        existingRecord.setId(15L);
        when(fileRecords.findByRunIdAndRelativePath(runId, path)).thenReturn(Optional.of(existingRecord));

        Finding finding = new Finding(
                "pmd", "7.0.0", "EmptyCatchBlock", "Medium",
                Severity.MEDIUM, "empty catch", path, new Span(1, 1, 3, 1),
                "Foo.test", "catch (Exception e) {}", "catch(Exception e){}", "fp123");

        service.saveFileAnalysis(runId, path, "java", "hash1", 20, "OK", null, List.of(finding));

        // Verifies previous findings for this file were deleted before inserting new ones
        verify(findings).deleteByFileId(existingRecord.getId());
    }

    @Test
    void recordingFileResultTwiceUpdatesExistingRecord() {
        Long repoId = 1L;
        String commit = "abc1234";
        String path = "src/Foo.java";
        String side = "AFTER";
        String analyzerVer = "7.0.0";
        String rulesetHash = "rulehash";

        WorkerFileResultEntity existing = new WorkerFileResultEntity(
                repoId, commit, path, side, analyzerVer, rulesetHash, "OK", 2, Instant.now());

        when(fileResults.findByRepositoryIdAndCommitHashAndFilePathAndSideAndAnalyzerVersionAndRulesetHash(
                repoId, commit, path, side, analyzerVer, rulesetHash)).thenReturn(Optional.of(existing));

        service.recordFileResult(repoId, commit, path, side, analyzerVer, rulesetHash, "OK", 2);

        verify(fileResults).save(existing);
    }
}
