package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.persistence.BenchmarkRunEntity;
import java.time.Instant;
import java.util.List;

/**
 * DTOs for the evaluation benchmark leaderboard and model comparison (Phase 6, spec section 7.4).
 */
public final class BenchmarkResponses {

    private BenchmarkResponses() {
    }

    public record BenchmarkRunResponse(
            long id,
            String benchmarkId,
            String datasetVersion,
            String conditionName,
            String modelName,
            String promptVersion,
            String retrievalStrategy,
            int sampleSize,
            double schemaValidRate,
            double diffAppliesRate,
            double parsesRate,
            double targetResolvedRate,
            double noNewFindingsRate,
            int medianDiffSize,
            double avgLatencyMs,
            long totalTokens,
            double estimatedCostUsd,
            String metricsJson,
            String notes,
            Instant createdAt) {

        public static BenchmarkRunResponse of(BenchmarkRunEntity entity) {
            return new BenchmarkRunResponse(
                    entity.getId(),
                    entity.getBenchmarkId(),
                    entity.getDatasetVersion(),
                    entity.getConditionName(),
                    entity.getModelName(),
                    entity.getPromptVersion(),
                    entity.getRetrievalStrategy(),
                    entity.getSampleSize(),
                    entity.getSchemaValidRate(),
                    entity.getDiffAppliesRate(),
                    entity.getParsesRate(),
                    entity.getTargetResolvedRate(),
                    entity.getNoNewFindingsRate(),
                    entity.getMedianDiffSize(),
                    entity.getAvgLatencyMs(),
                    entity.getTotalTokens(),
                    entity.getEstimatedCostUsd(),
                    entity.getMetricsJson(),
                    entity.getNotes(),
                    entity.getCreatedAt());
        }
    }

    public record CreateBenchmarkRunRequest(
            String benchmarkId,
            String datasetVersion,
            String conditionName,
            String modelName,
            String promptVersion,
            String retrievalStrategy,
            int sampleSize,
            double schemaValidRate,
            double diffAppliesRate,
            double parsesRate,
            double targetResolvedRate,
            double noNewFindingsRate,
            int medianDiffSize,
            double avgLatencyMs,
            long totalTokens,
            double estimatedCostUsd,
            String metricsJson,
            String notes) {
    }

    public record ModelCompareRequest(
            List<Long> modelIds) {
    }

    public record ModelComparisonItem(
            Long modelId,
            String modelLabel,
            String status,
            String errorMessage,
            SuggestionResponse suggestion,
            long latencyMs,
            int tokensIn,
            int tokensOut) {

        public static ModelComparisonItem success(Long modelId, String modelLabel, SuggestionResponse suggestion,
                                                  long latencyMs, int tokensIn, int tokensOut) {
            return new ModelComparisonItem(modelId, modelLabel, "SUCCESS", null, suggestion, latencyMs, tokensIn, tokensOut);
        }

        public static ModelComparisonItem failure(Long modelId, String modelLabel, String errorMessage) {
            return new ModelComparisonItem(modelId, modelLabel, "ERROR", errorMessage, null, 0, 0, 0);
        }
    }

    public record ModelCompareResponse(
            long findingId,
            List<ModelComparisonItem> comparisons) {
    }
}
