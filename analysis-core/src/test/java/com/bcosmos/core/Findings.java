package com.bcosmos.core;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.quality.Fingerprint;

/** Small factory so tests can build findings without repeating twelve arguments. */
public final class Findings {

    private Findings() {
    }

    /** A PMD finding with the given identity parts and line range; the fingerprint follows the spec recipe. */
    public static Finding pmd(String ruleId, String symbolQualifiedName, String snippet, int startLine, int endLine) {
        String normalized = snippet.replaceAll("\\s+", " ").trim();
        return new Finding("pmd", "net.sourceforge.pmd:pmd-java:7.28.0", ruleId, "MEDIUM", Severity.MEDIUM,
                ruleId + " message", "fixtures/Sample.java", new Span(startLine, 1, endLine, 1),
                symbolQualifiedName, snippet, normalized,
                Fingerprint.of(ruleId, symbolQualifiedName, normalized));
    }

    /** A finding without a fingerprint, to exercise the normalizer. */
    public static Finding withoutFingerprint(String ruleId, String symbolQualifiedName, String snippet, int line) {
        String normalized = snippet.replaceAll("\\s+", " ").trim();
        return new Finding("pmd", "net.sourceforge.pmd:pmd-java:7.28.0", ruleId, "MEDIUM", Severity.MEDIUM,
                ruleId + " message", "fixtures/Sample.java", new Span(line, 1, line, 1),
                symbolQualifiedName, snippet, normalized, null);
    }
}
