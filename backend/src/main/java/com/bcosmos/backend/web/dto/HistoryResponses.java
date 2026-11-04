package com.bcosmos.backend.web.dto;

import com.bcosmos.core.model.FindingTransitionType;
import java.time.Instant;
import java.util.List;

public final class HistoryResponses {

    private HistoryResponses() {
    }

    public record RunSummary(
            Long id,
            Long repositoryId,
            String commitHash,
            String status,
            int filesDiscovered,
            int filesParsed,
            int filesFailed,
            Instant startedAt,
            Instant finishedAt) {
    }

    public record FindingTransitionItem(
            Long id,
            Long fromFindingId,
            Long toFindingId,
            String fingerprint,
            String ruleId,
            String filePath,
            FindingTransitionType type,
            double confidence,
            String reason) {
    }

    public record SnapshotComparisonResponse(
            RunSummary fromRun,
            RunSummary toRun,
            int newCount,
            int resolvedCount,
            int persistentCount,
            int uncertainCount,
            List<FindingTransitionItem> transitions) {
    }
}
