package com.bcosmos.backend.ai.gateway;

import java.time.Duration;

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
    private final Duration instructedWait;

    public LlmException(String errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, Duration.ZERO, null);
    }

    public LlmException(String errorCode, String message, boolean retryable, Throwable cause) {
        this(errorCode, message, retryable, Duration.ZERO, cause);
    }

    protected LlmException(String errorCode, String message, boolean retryable, Duration instructedWait,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.instructedWait = instructedWait == null ? Duration.ZERO : instructedWait;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    /**
     * How long the provider itself asked us to wait before the next attempt.
     *
     * <p>{@link Duration#ZERO} covers both "it said nothing" and "it asked for no delay": either way the backoff is
     * the gateway's own guess, taken from {@code bcosmos.ai.initial-backoff}. A rate limit that does say something is
     * the one case where the provider's number beats ours (ADR 0040).</p>
     */
    public Duration instructedWait() {
        return instructedWait;
    }
}
