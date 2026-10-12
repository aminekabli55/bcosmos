package com.bcosmos.core.model;

import java.util.Objects;

/**
 * A normalized finding: one problem a tool reported in one file, in language-independent form (spec v2 section 4.1).
 *
 * <p>The two severities are deliberate: {@link #severity()} is the normalized scale the UI sorts and filters on,
 * {@link #originalSeverity()} keeps what the tool itself said, so a wrong mapping stays visible.</p>
 *
 * <p>{@link #filePath()} is always repository-relative and never absolute (spec section 10).</p>
 */
public record Finding(
        String tool,
        String toolVersion,
        String ruleId,
        String originalSeverity,
        Severity severity,
        String message,
        String filePath,
        Span span,
        String symbolQualifiedName,
        String snippet,
        String normalizedSnippet,
        String fingerprint) {

    public Finding {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(originalSeverity, "originalSeverity");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(span, "span");
        if (filePath.startsWith("/") || filePath.contains(":\\")) {
            throw new IllegalArgumentException("filePath must be repository-relative, never absolute");
        }
    }

    /** A copy with the given fingerprint (used by the normalizer when the adapter did not set one). */
    public Finding withFingerprint(String newFingerprint) {
        return new Finding(tool, toolVersion, ruleId, originalSeverity, severity, message, filePath, span,
                symbolQualifiedName, snippet, normalizedSnippet, newFingerprint);
    }

    /** Identity used by the fingerprint (spec section 4.2): rule + enclosing symbol + normalized snippet. */
    public String fingerprintInput() {
        return FingerprintInput.of(ruleId, symbolQualifiedName, normalizedSnippet);
    }

    /** Small helper so the fingerprint contract lives in exactly one place. */
    static final class FingerprintInput {

        private FingerprintInput() {
        }

        static String of(String ruleId, String symbolQualifiedName, String normalizedSnippet) {
            return ruleId + '\u001f' + (symbolQualifiedName == null ? "" : symbolQualifiedName)
                    + '\u001f' + (normalizedSnippet == null ? "" : normalizedSnippet);
        }
    }
}
