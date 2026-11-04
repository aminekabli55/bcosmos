package com.bcosmos.backend.ai.suggestion;

/**
 * The model's diff is not a diff: unreadable hunk header, unexpected lines, or line counts that disagree with the
 * hunk body.
 *
 * <p>It is a validation failure rather than an error to the caller: it becomes the {@code diffApplies = false}
 * verdict, which is exactly the kind of model-quality signal Phase 6 measures.</p>
 */
public class InvalidDiffException extends RuntimeException {

    public InvalidDiffException(String message) {
        super(message);
    }
}
