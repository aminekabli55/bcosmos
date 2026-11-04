package com.bcosmos.backend.ai.gateway;

/**
 * The provider needs a credential and neither source has one.
 *
 * <p>The message names the provider, the environment variable (when the row names one) and the way out. It never
 * contains a value (spec v2 section 10).</p>
 */
public class MissingApiKeyException extends LlmException {

    public MissingApiKeyException(String providerCode, String environmentVariable) {
        super(LlmErrorCode.MISSING_API_KEY, message(providerCode, environmentVariable), false);
    }

    private static String message(String providerCode, String environmentVariable) {
        if (environmentVariable == null || environmentVariable.isBlank()) {
            return "no API key for provider " + providerCode + ": store one in Settings, or name an environment "
                    + "variable for this provider";
        }
        return "no API key for provider " + providerCode + ": set " + environmentVariable + " in the environment, "
                + "or store one in Settings";
    }
}
