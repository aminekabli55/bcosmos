package com.bcosmos.core.pipeline;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import java.util.List;

/**
 * The result of analysing one file in one commit (spec v2 section 5.2, ADR 0025).
 *
 * <p>A failed parse is recorded as a result with an empty finding list and {@code parseStatus = FAILED},
 * rather than being dropped: "the file did not parse" is a fact the dataset and the idempotency gate need.</p>
 *
 * @param relativePath    repository-relative path with {@code /} separators
 * @param contentHash     SHA-256 hex of the source bytes; used as the idempotency and dedup key
 * @param languageId      e.g. {@code java}
 * @param lineCount       line count of the parsed source, 0 when the file could not be read
 * @param parseStatus     {@code OK} or {@code FAILED}
 * @param parseError      first parse error summary, or {@code null} when {@code parseStatus == OK}
 * @param findings        normalized findings; empty when the file failed to parse
 * @param analyzerVersion adapter's analyzer version string, for recording on the run
 * @param rulesetHash     adapter's rule-set hash, for the idempotency key
 */
public record CommitAnalysisResult(
        String relativePath,
        String contentHash,
        String languageId,
        int lineCount,
        ParseStatus parseStatus,
        String parseError,
        List<Finding> findings,
        String analyzerVersion,
        String rulesetHash) {

    public CommitAnalysisResult {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("relativePath must be repository-relative");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** Convenience: true when the file parsed and produced at least one finding. */
    public boolean hasFindings() {
        return parseStatus == ParseStatus.OK && !findings.isEmpty();
    }
}
