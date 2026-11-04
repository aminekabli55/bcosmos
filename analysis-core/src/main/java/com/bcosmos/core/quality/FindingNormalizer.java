package com.bcosmos.core.quality;

import com.bcosmos.core.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a raw tool output into the normalized finding list a run stores: every finding carries a fingerprint,
 * duplicates disappear and the order is stable.
 *
 * <p>Ordering is {@code (filePath, startLine, startColumn, ruleId, fingerprint)}. A stable order matters for
 * tests, for pagination and for comparing two runs.</p>
 */
public final class FindingNormalizer {

    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::filePath)
            .thenComparingInt(finding -> finding.span().startLine())
            .thenComparingInt(finding -> finding.span().startColumn())
            .thenComparing(Finding::ruleId)
            .thenComparing(Finding::fingerprint);

    /** Assigns missing fingerprints, removes duplicates and sorts deterministically. */
    public List<Finding> normalize(List<Finding> raw) {
        Objects.requireNonNull(raw, "raw");
        Map<String, Finding> byIdentity = new LinkedHashMap<>();
        for (Finding finding : raw) {
            Finding fingerprinted = finding.fingerprint() == null || finding.fingerprint().isBlank()
                    ? finding.withFingerprint(Fingerprint.of(finding.ruleId(), finding.symbolQualifiedName(),
                            finding.normalizedSnippet()))
                    : finding;
            byIdentity.putIfAbsent(identityOf(fingerprinted), fingerprinted);
        }
        List<Finding> normalized = new ArrayList<>(byIdentity.values());
        normalized.sort(ORDER);
        return List.copyOf(normalized);
    }

    /**
     * Two findings of the same rule, in the same file, on the same symbol and with the same normalized snippet are
     * the same finding; the line range is part of the key so two separate occurrences in one file stay separate.
     */
    private static String identityOf(Finding finding) {
        return finding.fingerprint() + '\u001f' + finding.filePath() + '\u001f'
                + finding.span().startLine() + '\u001f' + finding.span().endLine();
    }
}
