package com.bcosmos.backend.repository;

/**
 * The requested repository path cannot be analysed: it is missing, not a directory, outside the configured allowed
 * roots, or it escapes them through a symlink.
 *
 * <p>The message is deliberately generic and never repeats the path: absolute paths do not belong in responses
 * (spec v2 section 8) or in logs (section 10).</p>
 */
public class InvalidRepositoryPathException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRepositoryPathException(String message) {
        super(message);
    }

    public InvalidRepositoryPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
