package com.bcosmos.backend.persistence;

/**
 * How a model call ended, as stored on the usage row.
 *
 * <p>Failures are recorded on purpose: a model that keeps returning JSON the schema rejects is exactly what the
 * usage table has to show (spec v2 section 7.1). A call that was never sent - AI switched off, no model selected -
 * has no row at all, because there is no call to account for.</p>
 */
public enum LlmUsageOutcome {

    /** The call returned what the caller asked for. */
    SUCCESS,

    /** The model answered, but the answer did not validate against the schema (after the retry). */
    SCHEMA_INVALID,

    /** The provider rejected the call or failed. */
    PROVIDER_ERROR,

    /** The call did not finish inside the configured budget. */
    TIMEOUT
}
