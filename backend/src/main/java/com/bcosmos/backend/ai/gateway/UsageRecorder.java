package com.bcosmos.backend.ai.gateway;

/**
 * Where the gateway sends its usage rows (spec v2 section 7.1).
 *
 * <p>An interface rather than the repository itself so gateway tests can collect rows in memory and so the write can
 * be given its own transaction.</p>
 */
public interface UsageRecorder {

    /** Records one call. Implementations must not fail the caller: a lost usage row is not worth a lost answer. */
    void record(UsageRecord record);
}
