package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Symbol graph edge record (Phase 8, spec v2 section 4.1, table {@code relationship}).
 */
@Entity
@Table(name = "relationship")
public class RelationshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "source_symbol_id", nullable = false)
    private Long sourceSymbolId;

    @Column(name = "target_symbol_id")
    private Long targetSymbolId;

    @Column(name = "target_reference_name", nullable = false, length = 500)
    private String targetReferenceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private RelationshipType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 20)
    private ResolutionStatus resolutionStatus;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "evidence_file", nullable = false, length = 500)
    private String evidenceFile;

    @Column(name = "evidence_line", nullable = false)
    private int evidenceLine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RelationshipEntity() {
    }

    public RelationshipEntity(
            Long runId,
            Long sourceSymbolId,
            Long targetSymbolId,
            String targetReferenceName,
            RelationshipType type,
            ResolutionStatus resolutionStatus,
            double confidence,
            String evidenceFile,
            int evidenceLine) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.sourceSymbolId = Objects.requireNonNull(sourceSymbolId, "sourceSymbolId");
        this.targetSymbolId = targetSymbolId;
        this.targetReferenceName = Objects.requireNonNull(targetReferenceName, "targetReferenceName");
        this.type = Objects.requireNonNull(type, "type");
        this.resolutionStatus = Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        this.confidence = confidence;
        this.evidenceFile = Objects.requireNonNull(evidenceFile, "evidenceFile");
        this.evidenceLine = evidenceLine;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getSourceSymbolId() {
        return sourceSymbolId;
    }

    public Long getTargetSymbolId() {
        return targetSymbolId;
    }

    public void setTargetSymbolId(Long targetSymbolId) {
        this.targetSymbolId = targetSymbolId;
    }

    public String getTargetReferenceName() {
        return targetReferenceName;
    }

    public RelationshipType getType() {
        return type;
    }

    public ResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public void setResolutionStatus(ResolutionStatus resolutionStatus) {
        this.resolutionStatus = resolutionStatus;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getEvidenceFile() {
        return evidenceFile;
    }

    public int getEvidenceLine() {
        return evidenceLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
