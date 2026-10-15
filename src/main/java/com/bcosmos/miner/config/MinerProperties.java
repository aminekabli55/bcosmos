package com.bcosmos.miner.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the repository miner (spec v2 section 10, ADR 0023, ADR 0026).
 */
@ConfigurationProperties(prefix = "bcosmos.miner")
public record MinerProperties(
        String allowedHosts,
        String cloneDir,
        int commitWindow,
        int maxRetryAttempts,
        long initialBackoffMs,
        double backoffMultiplier) {

    public MinerProperties {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            allowedHosts = "github.com,gitlab.com";
        }
        if (cloneDir == null || cloneDir.isBlank()) {
            cloneDir = "data/repos";
        }
        if (commitWindow < 1) {
            commitWindow = 50;
        }
        if (maxRetryAttempts < 0) {
            maxRetryAttempts = 3;
        }
        if (initialBackoffMs <= 0) {
            initialBackoffMs = 500;
        }
        if (backoffMultiplier <= 1.0) {
            backoffMultiplier = 2.0;
        }
    }

    public Set<String> allowedHostSet() {
        return Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public boolean isHostAllowed(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase();
        return allowedHostSet().stream()
                .anyMatch(allowed -> normalized.equals(allowed.toLowerCase()) || normalized.endsWith("." + allowed.toLowerCase()));
    }
}
