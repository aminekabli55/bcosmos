package com.bcosmos.backend.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The Phase 5 analytics contract (spec v2 section 8, {@code GET /api/analytics/*}).
 *
 * <p>Every response that shows a number also carries the snapshot it came from: the {@link Overview} names the
 * {@code datasetVersion} and the time it was generated, and the screen shows both. A chart without its snapshot is a
 * number nobody can reproduce.</p>
 *
 * <p>Only DTOs leave the API: the tables behind them are read with plain SQL, so the row shape never becomes part of
 * the contract by accident.</p>
 */
public final class AnalyticsResponses {

    private AnalyticsResponses() {
    }

    /**
     * The snapshot header and the headline numbers.
     *
     * <p>{@code available} is false when no Spark gold load has ever run. That is not an error: it is the normal
     * state of a fresh installation, and the screen shows how to fill it instead of an error banner.</p>
     *
     * <p>{@code repositories} is the count and {@code repositoryRefs} is the list of repositories that have rows in
     * the snapshot: two fields with two names, because a chart that read the count as a list would render nothing and
     * a JSON reader would not notice.</p>
     */
    public record Overview(
            boolean available,
            String datasetVersion,
            Instant generatedAt,
            String source,
            Integer drilldownCommits,
            long repositories,
            long rules,
            long findings,
            long measuredLines,
            long parseFailures,
            long triples,
            List<SeverityCount> severityMix,
            List<RuleStat> topRules,
            List<PipelineStat> pipelineStats,
            List<RepositoryRef> repositoryRefs,
            List<DatasetSplit> splits) {

        public static Overview unavailable() {
            return new Overview(false, null, null, null, null, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(),
                    List.of(), List.of());
        }
    }

    /** One repository that has rows in the current snapshot, for the filter dropdown. */
    public record RepositoryRef(long repositoryId, String name, String sourceType, String licenseSpdx) {
    }

    public record SeverityCount(String severity, long findings) {
    }

    /** A named count about the run itself (rows kept, rows rejected and why). */
    public record PipelineStat(String key, long value) {
    }

    /** Findings per rule and severity, per KLOC (spec v2 section 6.1). */
    public record RuleStat(
            long repositoryId,
            String ruleId,
            String severity,
            long findings,
            long filesAffected,
            long measuredLines,
            Double findingsPerKloc,
            long parseFailures,
            int rank) {
    }

    /** One UTC week of the analysis window (spec v2 section 6.1). */
    public record TrendPoint(
            long repositoryId,
            LocalDate bucketStart,
            long findings,
            long filesAnalysed,
            long measuredLines,
            Double densityPerKloc,
            long parseFailures) {
    }

    /** One bar of the commits-until-resolved histogram; a null distance is the lower-bound row. */
    public record TimeToFixBucket(
            long repositoryId,
            String ruleId,
            Integer commitsUntilResolved,
            long resolvedPairs,
            long observedIntroductions) {
    }

    /** One ranked file with every component of the triage heuristic visible (spec v2 section 6.3). */
    public record Hotspot(
            long repositoryId,
            String filePath,
            double score,
            int rankInRepository,
            long churn,
            Double churnScore,
            Double churnContribution,
            long complexityProxy,
            Double complexityScore,
            Double complexityContribution,
            long openFindings,
            Double openFindingsScore,
            Double openFindingsContribution,
            boolean dependencyMeasured,
            double weightSumMeasured,
            String latestCommitHash,
            String weights) {
    }

    /** One split of the fix-triple dataset (spec v2 section 6.2). */
    public record DatasetSplit(
            String split,
            long triples,
            int repositories,
            int rules,
            long snippetChanged,
            long introductionsUnobserved,
            int maxChangedLines) {
    }

    /** Which repository is in which split, with the licence reported for it. */
    public record DatasetRepository(
            String split,
            long repositoryId,
            String name,
            String licenseSpdx,
            long triples) {
    }

    /** One drill-down record: the finding an aggregate was computed from. */
    public record DrilldownFinding(
            long id,
            long repositoryId,
            String commitHash,
            String filePath,
            String side,
            String ruleId,
            String severity,
            String symbol,
            String fingerprint,
            int startLine,
            int endLine,
            Integer lineCount,
            Instant occurredAt,
            String snippet) {
    }
}
