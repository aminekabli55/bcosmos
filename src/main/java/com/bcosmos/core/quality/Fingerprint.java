package com.bcosmos.core.quality;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Finding identity across commits (spec v2 section 4.2):
 * {@code sha256(ruleId + enclosingSymbolQualifiedName + normalizedFlaggedSnippet)}.
 *
 * <p>Line numbers are deliberately excluded, so moving code does not create a new finding. The hashing is
 * language-independent: the adapter only supplies the normalized snippet.</p>
 */
public final class Fingerprint {

    private static final char FIELD_SEPARATOR = '\u001f';

    private Fingerprint() {
    }

    /** Fingerprint of a finding, from its rule, enclosing symbol and normalized snippet. */
    public static String of(String ruleId, String symbolQualifiedName, String normalizedSnippet) {
        return sha256Hex(ruleId + FIELD_SEPARATOR
                + (symbolQualifiedName == null ? "" : symbolQualifiedName) + FIELD_SEPARATOR
                + (normalizedSnippet == null ? "" : normalizedSnippet));
    }

    /** Hex sha256 of a UTF-8 text. */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Hex sha256 of raw bytes. */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every Java runtime; failing here is a broken JVM, not a user error.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
