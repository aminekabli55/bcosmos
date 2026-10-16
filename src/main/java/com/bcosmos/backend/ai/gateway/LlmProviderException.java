package com.bcosmos.backend.ai.gateway;

import java.time.Duration;

/**
 * The provider rejected the call or failed.
 *
 * <p>The message carries the provider and the HTTP status, never the response body: an error body can echo the
 * request, and the request contains source code (spec v2 section 10).</p>
 */
public class LlmProviderException extends LlmException {

    public LlmProviderException(String errorCode, String message, boolean retryable) {
        super(errorCode, message, retryable);
    }

    public LlmProviderException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(errorCode, message, retryable, cause);
    }

    public LlmProviderException(String errorCode, String message, boolean retryable, Duration instructedWait,
            Throwable cause) {
        super(errorCode, message, retryable, instructedWait, cause);
    }

    /**
     * A provider that refused the call for now (HTTP 429), carrying the wait it asked for.
     *
     * <p>What the provider said is quoted in the message - "it asks to wait 42s" - because that is the difference
     * between the operator re-running in a minute and re-running in an hour, and because a quota that resets daily
     * cannot be retried into success at all (ADR 0040).</p>
     */
    static LlmProviderException rateLimited(String provider, Duration instructedWait, Throwable cause) {
        String message = provider + " rate limited the call (HTTP 429)";
        if (instructedWait != null && !instructedWait.isZero()) {
            message = message + "; it asks to wait " + RetryWait.describe(instructedWait) + " before the next attempt";
        }
        return new LlmProviderException(LlmErrorCode.RATE_LIMITED, message, true, instructedWait, cause);
    }
}
