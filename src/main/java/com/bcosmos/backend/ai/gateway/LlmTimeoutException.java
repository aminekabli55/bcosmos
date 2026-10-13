package com.bcosmos.backend.ai.gateway;

/** A call did not finish inside the configured budget, or a retry would have exceeded it. */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message, boolean retryable) {
        super(LlmErrorCode.TIMEOUT, message, retryable);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(LlmErrorCode.TIMEOUT, message, true, cause);
    }
}
