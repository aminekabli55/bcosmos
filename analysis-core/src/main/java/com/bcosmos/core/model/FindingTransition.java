package com.bcosmos.core.model;

import java.util.Objects;

/**
 * Finding transition record matching finding states between two analysis snapshots (Phase 8, spec section 4.1 & 4.2).
 *
 * @param fromFindingId   ID of the finding in the previous snapshot (null if NEW)
 * @param toFindingId     ID of the finding in the subsequent snapshot (null if RESOLVED or UNCERTAIN without to-finding)
 * @param fingerprint     finding fingerprint identifying the finding across commits
 * @param ruleId          rule identifier (e.g. EmptyCatchBlock)
 * @param filePath        relative file path
 * @param type            transition type (NEW, RESOLVED, PERSISTENT, UNCERTAIN)
 * @param confidence      confidence score (0.0 to 1.0)
 * @param reason          human-readable explanation for why this classification was made
 */
public record FindingTransition(
        Long fromFindingId,
        Long toFindingId,
        String fingerprint,
        String ruleId,
        String filePath,
        FindingTransitionType type,
        double confidence,
        String reason) {

    public FindingTransition {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0, got " + confidence);
        }
    }
}
