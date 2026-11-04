package com.bcosmos.backend.history;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.FindingTransitionEntity;
import com.bcosmos.backend.persistence.FindingTransitionJpaRepository;
import com.bcosmos.backend.web.dto.HistoryResponses.FindingTransitionItem;
import com.bcosmos.backend.web.dto.HistoryResponses.RunSummary;
import com.bcosmos.backend.web.dto.HistoryResponses.SnapshotComparisonResponse;
import com.bcosmos.core.model.FindingTransition;
import com.bcosmos.core.model.FindingTransitionType;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.quality.FindingTransitionCalculator;
import com.bcosmos.core.quality.FindingTransitionCalculator.FindingItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Compares two analysis snapshots, computing finding lifecycle transitions (NEW, RESOLVED, PERSISTENT, UNCERTAIN)
 * per spec section 4.1 & 4.2.
 */
@Service
public class HistoryService {

    private final AnalysisRunJpaRepository runs;
    private final FindingJpaRepository findings;
    private final FileRecordJpaRepository fileRecords;
    private final FindingTransitionJpaRepository transitions;

    public HistoryService(
            AnalysisRunJpaRepository runs,
            FindingJpaRepository findings,
            FileRecordJpaRepository fileRecords,
            FindingTransitionJpaRepository transitions) {
        this.runs = runs;
        this.findings = findings;
        this.fileRecords = fileRecords;
        this.transitions = transitions;
    }

    @Transactional
    public SnapshotComparisonResponse compareSnapshots(Long fromRunId, Long toRunId) {
        AnalysisRunEntity fromRun = runs.findById(fromRunId)
                .orElseThrow(() -> new NoSuchElementException("Baseline run not found: " + fromRunId));
        AnalysisRunEntity toRun = runs.findById(toRunId)
                .orElseThrow(() -> new NoSuchElementException("Target run not found: " + toRunId));

        List<FindingTransitionEntity> stored = transitions.findByFromRunIdAndToRunId(fromRunId, toRunId);
        if (stored.isEmpty()) {
            stored = computeAndStoreTransitions(fromRun, toRun);
        }

        int newCount = 0;
        int resolvedCount = 0;
        int persistentCount = 0;
        int uncertainCount = 0;

        List<FindingTransitionItem> items = new ArrayList<>();
        for (FindingTransitionEntity entity : stored) {
            switch (entity.getType()) {
                case NEW -> newCount++;
                case RESOLVED -> resolvedCount++;
                case PERSISTENT -> persistentCount++;
                case UNCERTAIN -> uncertainCount++;
            }
            items.add(new FindingTransitionItem(
                    entity.getId(),
                    entity.getFromFindingId(),
                    entity.getToFindingId(),
                    entity.getFingerprint(),
                    entity.getRuleId(),
                    entity.getFilePath(),
                    entity.getType(),
                    entity.getConfidence(),
                    entity.getReason()));
        }

        return new SnapshotComparisonResponse(
                toRunSummary(fromRun),
                toRunSummary(toRun),
                newCount,
                resolvedCount,
                persistentCount,
                uncertainCount,
                items);
    }

    private List<FindingTransitionEntity> computeAndStoreTransitions(AnalysisRunEntity fromRun, AnalysisRunEntity toRun) {
        List<FindingEntity> fromFindingEntities = findings.findByRunId(fromRun.getId());
        List<FindingEntity> toFindingEntities = findings.findByRunId(toRun.getId());

        List<FileRecordEntity> fromFiles = fileRecords.findByRunId(fromRun.getId());
        List<FileRecordEntity> toFiles = fileRecords.findByRunId(toRun.getId());

        Map<Long, String> fromFilePaths = new HashMap<>();
        for (FileRecordEntity f : fromFiles) {
            fromFilePaths.put(f.getId(), f.getRelativePath());
        }

        Map<Long, String> toFilePaths = new HashMap<>();
        Set<String> toFailedFiles = new HashSet<>();
        for (FileRecordEntity f : toFiles) {
            toFilePaths.put(f.getId(), f.getRelativePath());
            if (f.getParseStatus() != ParseStatus.OK) {
                toFailedFiles.add(f.getRelativePath());
            }
        }

        List<FindingItem> fromItems = fromFindingEntities.stream()
                .map(f -> new FindingItem(
                        f.getId(),
                        f.getFingerprint(),
                        f.getRuleId(),
                        fromFilePaths.getOrDefault(f.getFileId(), "unknown"),
                        f.getMessage()))
                .toList();

        List<FindingItem> toItems = toFindingEntities.stream()
                .map(f -> new FindingItem(
                        f.getId(),
                        f.getFingerprint(),
                        f.getRuleId(),
                        toFilePaths.getOrDefault(f.getFileId(), "unknown"),
                        f.getMessage()))
                .toList();

        List<FindingTransition> calculated = FindingTransitionCalculator.compare(
                fromItems,
                toItems,
                toFailedFiles,
                Set.of()); // all active rules

        List<FindingTransitionEntity> entities = calculated.stream()
                .map(t -> new FindingTransitionEntity(
                        fromRun.getId(),
                        toRun.getId(),
                        t.fromFindingId(),
                        t.toFindingId(),
                        t.fingerprint(),
                        t.ruleId(),
                        t.filePath(),
                        t.type(),
                        t.confidence(),
                        t.reason()))
                .toList();

        return transitions.saveAll(entities);
    }

    private RunSummary toRunSummary(AnalysisRunEntity r) {
        return new RunSummary(
                r.getId(),
                r.getRepositoryId(),
                r.getCommitHash(),
                r.getStatus().name(),
                r.getFilesDiscovered(),
                r.getFilesParsed(),
                r.getFilesFailed(),
                r.getStartedAt(),
                r.getFinishedAt());
    }
}
