package com.bcosmos.core.events;

/**
 * The event kinds that travel on Kafka (spec v2 section 5.2).
 *
 * <p>The enum name is the wire value: the envelope carries {@code eventType} so a consumer can route a message
 * without guessing from the topic, and the topics are named after the same words
 * ({@code repo.analysis.requested.v1} → {@link #ANALYSIS_REQUESTED}).</p>
 */
public enum EventType {

    /** backend → worker: analyse one registered local repository (full run). */
    ANALYSIS_REQUESTED,

    /** backend → miner: clone and walk one public repository (spec v2 section 12, Phase 4). */
    MINE_REQUESTED,

    /** miner → worker: one commit whose Java files changed. */
    COMMIT_DISCOVERED,

    /** worker → Spark streaming: the findings of one (commit, file, side). */
    FINDINGS
}
