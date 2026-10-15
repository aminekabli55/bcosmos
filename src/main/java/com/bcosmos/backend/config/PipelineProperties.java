package com.bcosmos.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether the Kafka pipeline is used, and how its topics are created (spec v2 section 5).
 *
 * <p>Off by default, and off means the backend is exactly the Phase 1–3 application: analyses run in-process and no
 * broker has to be running. Turning it on is a deployment decision, not a code change.</p>
 *
 * @param enabled   use Kafka instead of the in-process dispatcher
 * @param partitions topic partitions; must be at least the intended worker count so consumers can scale out
 * @param replicas  replication factor; 1 is correct for the single-node development broker
 */
@ConfigurationProperties(prefix = "bcosmos.kafka")
public record PipelineProperties(boolean enabled, int partitions, int replicas) {

    public PipelineProperties {
        partitions = partitions < 1 ? 3 : partitions;
        replicas = replicas < 1 ? 1 : replicas;
    }
}
