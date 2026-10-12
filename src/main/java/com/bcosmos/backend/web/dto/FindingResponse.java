package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.core.model.Severity;

/**
 * A finding as the Quality screen consumes it: rule, severity, evidence (file, line range, snippet) and identity
 * (fingerprint), with the original tool severity kept next to the normalized one (spec v2 sections 4.1 to 4.3).
 *
 * @param filePath  repository-relative path; an absolute path never appears in a response
 * @param snippet   the flagged source lines stored with the finding, so the preview matches the analysed revision
 */
public record FindingResponse(
        Long id,
        String ruleId,
        Severity severity,
        String originalSeverity,
        String tool,
        String toolVersion,
        String message,
        String filePath,
        int startLine,
        int endLine,
        String symbolQualifiedName,
        String snippet,
        String fingerprint,
        TriageStatus triageStatus) {

    public static FindingResponse of(FindingEntity entity, String filePath) {
        return new FindingResponse(
                entity.getId(),
                entity.getRuleId(),
                entity.getSeverity(),
                entity.getOriginalSeverity(),
                entity.getTool(),
                entity.getToolVersion(),
                entity.getMessage(),
                filePath,
                entity.getSpan().startLine(),
                entity.getSpan().endLine(),
                entity.getSymbolQualifiedName(),
                entity.getSnippet(),
                entity.getFingerprint(),
                entity.getTriageStatus());
    }
}
