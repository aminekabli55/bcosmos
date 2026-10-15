package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * An evaluation benchmark run measuring a model condition on a versioned dataset (spec v2 section 7.4).
 */
@Entity
@Table(name = "benchmark_run")
public class BenchmarkRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "benchmark_id", nullable = false, length = 100)
    private String benchmarkId;

    @Column(name = "dataset_version", nullable = false, length = 100)
    private String datasetVersion;

    @Column(name = "condition_name", nullable = false, length = 100)
    private String conditionName;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Column(name = "retrieval_strategy", nullable = false, length = 50)
    private String retrievalStrategy;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "schema_valid_rate", nullable = false)
    private Double schemaValidRate;

    @Column(name = "diff_applies_rate", nullable = false)
    private Double diffAppliesRate;

    @Column(name = "parses_rate", nullable = false)
    private Double parsesRate;

    @Column(name = "target_resolved_rate", nullable = false)
    private Double targetResolvedRate;

    @Column(name = "no_new_findings_rate", nullable = false)
    private Double noNewFindingsRate;

    @Column(name = "median_diff_size", nullable = false)
    private Integer medianDiffSize;

    @Column(name = "avg_latency_ms", nullable = false)
    private Double avgLatencyMs;

    @Column(name = "total_tokens", nullable = false)
    private Long totalTokens;

    @Column(name = "estimated_cost_usd", nullable = false)
    private Double estimatedCostUsd;

    @Column(name = "metrics_json", nullable = false, columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BenchmarkRunEntity() {
    }

    public BenchmarkRunEntity(String benchmarkId, String datasetVersion, String conditionName, String modelName,
                              String promptVersion, String retrievalStrategy, Integer sampleSize,
                              Double schemaValidRate, Double diffAppliesRate, Double parsesRate,
                              Double targetResolvedRate, Double noNewFindingsRate, Integer medianDiffSize,
                              Double avgLatencyMs, Long totalTokens, Double estimatedCostUsd,
                              String metricsJson, String notes, Instant createdAt) {
        this.benchmarkId = Objects.requireNonNull(benchmarkId, "benchmarkId");
        this.datasetVersion = Objects.requireNonNull(datasetVersion, "datasetVersion");
        this.conditionName = Objects.requireNonNull(conditionName, "conditionName");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.promptVersion = Objects.requireNonNull(promptVersion, "promptVersion");
        this.retrievalStrategy = Objects.requireNonNull(retrievalStrategy, "retrievalStrategy");
        this.sampleSize = Objects.requireNonNull(sampleSize, "sampleSize");
        this.schemaValidRate = Objects.requireNonNull(schemaValidRate, "schemaValidRate");
        this.diffAppliesRate = Objects.requireNonNull(diffAppliesRate, "diffAppliesRate");
        this.parsesRate = Objects.requireNonNull(parsesRate, "parsesRate");
        this.targetResolvedRate = Objects.requireNonNull(targetResolvedRate, "targetResolvedRate");
        this.noNewFindingsRate = Objects.requireNonNull(noNewFindingsRate, "noNewFindingsRate");
        this.medianDiffSize = Objects.requireNonNull(medianDiffSize, "medianDiffSize");
        this.avgLatencyMs = Objects.requireNonNull(avgLatencyMs, "avgLatencyMs");
        this.totalTokens = Objects.requireNonNull(totalTokens, "totalTokens");
        this.estimatedCostUsd = Objects.requireNonNull(estimatedCostUsd, "estimatedCostUsd");
        this.metricsJson = Objects.requireNonNull(metricsJson, "metricsJson");
        this.notes = notes;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getBenchmarkId() {
        return benchmarkId;
    }

    public String getDatasetVersion() {
        return datasetVersion;
    }

    public String getConditionName() {
        return conditionName;
    }

    public String getModelName() {
        return modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public Integer getSampleSize() {
        return sampleSize;
    }

    public Double getSchemaValidRate() {
        return schemaValidRate;
    }

    public Double getDiffAppliesRate() {
        return diffAppliesRate;
    }

    public Double getParsesRate() {
        return parsesRate;
    }

    public Double getTargetResolvedRate() {
        return targetResolvedRate;
    }

    public Double getNoNewFindingsRate() {
        return noNewFindingsRate;
    }

    public Integer getMedianDiffSize() {
        return medianDiffSize;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public Double getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
