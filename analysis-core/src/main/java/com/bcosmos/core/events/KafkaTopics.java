package com.bcosmos.core.events;

/**
 * Topic names of the pipeline (spec v2 section 5.1). One place, so a producer and a consumer in different modules
 * can never disagree about a topic.
 */
public final class KafkaTopics {

    /** backend → worker: full analysis of a registered local repository. Keyed by repository id. */
    public static final String ANALYSIS_REQUESTED = "repo.analysis.requested.v1";

    /** backend → miner: clone and walk one public repository. Keyed by remote URL. */
    public static final String MINE_REQUESTED = "repo.mine.requested.v1";

    /** miner → worker: one commit to analyse, changed Java files only. Keyed by repository id. */
    public static final String COMMIT_DISCOVERED = "repo.commit.discovered.v1";

    /** worker → Spark streaming: findings per (commit, file, side). Keyed by repository id. */
    public static final String FINDINGS = "analysis.findings.v1";

    /** Suffix every dead-letter topic adds to the topic it serves (spec v2 section 5.1: {@code *.dlq.v1}). */
    public static final String DLQ_SUFFIX = ".dlq.v1";

    private KafkaTopics() {
    }

    /** The dead-letter topic for {@code topic}, e.g. {@code analysis.findings.v1.dlq.v1}. */
    public static String deadLetterOf(String topic) {
        return topic + DLQ_SUFFIX;
    }
}
