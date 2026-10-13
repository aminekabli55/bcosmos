package com.bcosmos.backend.ai.gateway;

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
}
