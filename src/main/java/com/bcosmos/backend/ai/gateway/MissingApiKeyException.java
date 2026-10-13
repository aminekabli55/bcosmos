package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProvider;

/**
 * The provider needs a credential and the environment does not have one.
 *
 * <p>The message names the environment variable, never a value (spec v2 section 10).</p>
 */
public class MissingApiKeyException extends LlmException {

    public MissingApiKeyException(LlmProvider provider, String environmentVariable) {
        super(LlmErrorCode.MISSING_API_KEY,
                "no API key for provider " + provider + ": set " + environmentVariable + " in the environment", false);
    }
}
