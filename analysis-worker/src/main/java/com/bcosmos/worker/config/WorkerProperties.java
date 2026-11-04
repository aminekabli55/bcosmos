package com.bcosmos.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the analysis worker (spec v2 section 5.3, ADR 0026).
 */
@ConfigurationProperties(prefix = "bcosmos.worker")
public record WorkerProperties(
        int maxRetryAttempts,
        long initialBackoffMs,
        double backoffMultiplier,
        String cloneDir) {

    public WorkerProperties {
        if (maxRetryAttempts < 0) {
            maxRetryAttempts = 3;
        }
        if (initialBackoffMs <= 0) {
            initialBackoffMs = 500;
        }
        if (backoffMultiplier <= 1.0) {
            backoffMultiplier = 2.0;
        }
        if (cloneDir == null || cloneDir.isBlank()) {
            cloneDir = "data/repos";
        }
    }
}
