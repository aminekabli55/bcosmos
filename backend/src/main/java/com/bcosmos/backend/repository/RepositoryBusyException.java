package com.bcosmos.backend.repository;

/**
 * A repository cannot be removed while an analysis of it is still queued or running (ADR 0037).
 *
 * <p>Deleting the row mid-run would leave a runner writing findings for a repository that no longer exists, so the
 * request is refused with a conflict instead of racing the runner. The message says what to do, and never repeats a
 * filesystem path (spec v2 sections 8 and 10).</p>
 */
public class RepositoryBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RepositoryBusyException(String message) {
        super(message);
    }
}
