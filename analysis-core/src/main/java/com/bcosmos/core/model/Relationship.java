package com.bcosmos.core.model;

import java.util.Objects;

/**
 * Structural or semantic edge between symbols or unresolved external targets (Phase 8, spec v2 section 3.1 & 4.1).
 *
 * @param sourceQualifiedName   qualified name of the source symbol (e.g. {@code com.acme.UserService.findById})
 * @param targetQualifiedName   qualified name of the target symbol if resolved; null if unresolved/external
 * @param targetReferenceName   raw target name as written in code (e.g. {@code save}, {@code UserRepository})
 * @param type                  relationship type (CALLS, EXTENDS, IMPLEMENTS, USES_TYPE)
 * @param resolutionStatus      resolution status (RESOLVED, UNRESOLVED, AMBIGUOUS)
 * @param confidence            resolution confidence (1.0 for exact, 0.7 for heuristic, 0.5 for ambiguous)
 * @param evidenceFile          repository-relative file path where this relationship was observed
 * @param evidenceLine          1-based line number of the call or reference site
 */
public record Relationship(
        String sourceQualifiedName,
        String targetQualifiedName,
        String targetReferenceName,
        RelationshipType type,
        ResolutionStatus resolutionStatus,
        double confidence,
        String evidenceFile,
        int evidenceLine) {

    public Relationship {
        Objects.requireNonNull(sourceQualifiedName, "sourceQualifiedName");
        Objects.requireNonNull(targetReferenceName, "targetReferenceName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(resolutionStatus, "resolutionStatus");
        Objects.requireNonNull(evidenceFile, "evidenceFile");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0, got " + confidence);
        }
    }
}
