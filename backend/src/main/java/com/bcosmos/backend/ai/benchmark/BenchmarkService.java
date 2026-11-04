package com.bcosmos.backend.ai.benchmark;

import com.bcosmos.backend.persistence.BenchmarkRunEntity;
import com.bcosmos.backend.persistence.BenchmarkRunJpaRepository;
import com.bcosmos.backend.web.dto.BenchmarkResponses.BenchmarkRunResponse;
import com.bcosmos.backend.web.dto.BenchmarkResponses.CreateBenchmarkRunRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing model benchmark runs and leaderboard metrics (Phase 6, spec section 7.4).
 */
@Service
public class BenchmarkService {

    private final BenchmarkRunJpaRepository runs;

    public BenchmarkService(BenchmarkRunJpaRepository runs) {
        this.runs = runs;
    }

    /** Returns all evaluated conditions ranked by target finding resolved rate (primary metric). */
    @Transactional(readOnly = true)
    public List<BenchmarkRunResponse> getLeaderboard() {
        return runs.findAllByOrderByTargetResolvedRateDesc().stream()
                .map(BenchmarkRunResponse::of)
                .toList();
    }

    /** Returns all runs for a specific dataset version. */
    @Transactional(readOnly = true)
    public List<BenchmarkRunResponse> getRunsByDataset(String datasetVersion) {
        return runs.findByDatasetVersionOrderByTargetResolvedRateDesc(datasetVersion).stream()
                .map(BenchmarkRunResponse::of)
                .toList();
    }

    /** Records a new benchmark run result (e.g. from ai-lab/eval harness). */
    @Transactional
    public BenchmarkRunResponse recordRun(CreateBenchmarkRunRequest req) {
        BenchmarkRunEntity entity = new BenchmarkRunEntity(
                req.benchmarkId(),
                req.datasetVersion(),
                req.conditionName(),
                req.modelName(),
                req.promptVersion(),
                req.retrievalStrategy(),
                req.sampleSize(),
                req.schemaValidRate(),
                req.diffAppliesRate(),
                req.parsesRate(),
                req.targetResolvedRate(),
                req.noNewFindingsRate(),
                req.medianDiffSize(),
                req.avgLatencyMs(),
                req.totalTokens(),
                req.estimatedCostUsd(),
                req.metricsJson(),
                req.notes(),
                Instant.now()
        );
        return BenchmarkRunResponse.of(runs.save(entity));
    }
}
