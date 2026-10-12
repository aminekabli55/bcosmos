package com.bcosmos.core;

/**
 * A file could not be analyzed even though it was read successfully, for example because the analyzer itself
 * reported a processing error.
 *
 * <p>The caller is expected to record this against the file and the run instead of dropping the file: failures are
 * counted and shown, never hidden (spec v2 section 12, Phase 1).</p>
 */
public class AnalysisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
