package com.bcosmos.backend.ai.gateway;

/**
 * Base type for every gateway failure.
 *
 * <p>{@code errorCode} is what the usage row and the problem detail carry; {@code retryable} says whether the
 * gateway may try the provider again. Messages must never contain a prompt, source code or an API key
 * (spec v2 section 10).</p>
 */
public class LlmException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public LlmException(String errorCode, String message, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public LlmException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
