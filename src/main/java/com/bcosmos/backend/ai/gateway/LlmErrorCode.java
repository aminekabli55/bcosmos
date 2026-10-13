package com.bcosmos.backend.ai.gateway;


public final class LlmErrorCode {

    /** The AI layer is switched off, so nothing was sent. */
    public static final String DISABLED = "ai-disabled";

    /** The configured model row is not enabled. */
    public static final String MODEL_DISABLED = "model-disabled";

    /** The provider has no API key in the environment. */
    public static final String MISSING_API_KEY = "missing-api-key";

    /** The provider rejected the credentials. */
    public static final String UNAUTHORIZED = "provider-unauthorized";

    /** The provider answered with an error we do not retry. */
    public static final String PROVIDER_REJECTED = "provider-rejected";

    /** The provider failed, or kept failing after the retries. */
    public static final String PROVIDER_ERROR = "provider-error";

    /** The call did not finish inside the configured budget. */
    public static final String TIMEOUT = "timeout";

    /** The answer never validated against the schema, not even after the retry. */
    public static final String SCHEMA_INVALID = "schema-invalid";

    /** The provider answered with something the adapter could not read. */
    public static final String MALFORMED_RESPONSE = "malformed-provider-response";

    private LlmErrorCode() {
    }
}
