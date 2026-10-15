package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.FindingTransitionType;
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
 * Finding lifecycle transition record between two analysis runs (Phase 8, spec section 4.1 & 4.2).
 */
@Entity
@Table(name = "finding_transition")
public class FindingTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_run_id", nullable = false)
    private Long fromRunId;

    @Column(name = "to_run_id", nullable = false)
    private Long toRunId;

    @Column(name = "from_finding_id")
    private Long fromFindingId;

    @Column(name = "to_finding_id")
    private Long toFindingId;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private FindingTransitionType type;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FindingTransitionEntity() {
    }

    public FindingTransitionEntity(
            Long fromRunId,
            Long toRunId,
            Long fromFindingId,
            Long toFindingId,
            String fingerprint,
            String ruleId,
            String filePath,
            FindingTransitionType type,
            double confidence,
            String reason) {
        this.fromRunId = Objects.requireNonNull(fromRunId, "fromRunId");
        this.toRunId = Objects.requireNonNull(toRunId, "toRunId");
        this.fromFindingId = fromFindingId;
        this.toFindingId = toFindingId;
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.type = Objects.requireNonNull(type, "type");
        this.confidence = confidence;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getFromRunId() {
        return fromRunId;
    }

    public Long getToRunId() {
        return toRunId;
    }

    public Long getFromFindingId() {
        return fromFindingId;
    }

    public Long getToFindingId() {
        return toFindingId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getFilePath() {
        return filePath;
    }

    public FindingTransitionType getType() {
        return type;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
