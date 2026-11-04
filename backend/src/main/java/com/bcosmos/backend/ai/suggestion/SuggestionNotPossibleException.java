package com.bcosmos.backend.ai.suggestion;

/**
 * A suggestion was asked for, but the run does not carry what one needs: the stored source of the finding's file is
 * missing, or the file never parsed.
 *
 * <p>The message says which analysis to re-run instead of returning an empty suggestion. Runs made before Phase 3
 * stored no source, and re-running the analysis is the fix.</p>
 */
public class SuggestionNotPossibleException extends RuntimeException {

    public SuggestionNotPossibleException(String message) {
        super(message);
    }
}
