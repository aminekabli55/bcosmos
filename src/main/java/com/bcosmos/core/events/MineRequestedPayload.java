package com.bcosmos.core.events;

/**
 * What the miner needs to clone and walk one public repository (spec v2 section 12, Phase 4).
 *
 * <p>The spec's topic table has no entry for "start mining": {@code POST /api/corpus/ingest} has to reach the miner
 * somehow, and the miner deliberately holds no database, so the request travels as an event on its own topic.</p>
 *
 * @param remoteUrl    an HTTPS URL; the miner refuses a host that is not on its allowlist
 * @param commitWindow how many commits back to walk, newest first
 */
public record MineRequestedPayload(String remoteUrl, String defaultBranch, int commitWindow, Long repositoryId) {

    public MineRequestedPayload {
        if (remoteUrl == null || !remoteUrl.startsWith("https://")) {
            throw new IllegalArgumentException("remoteUrl must be an https URL");
        }
        if (commitWindow < 1) {
            throw new IllegalArgumentException("commitWindow must be at least 1");
        }
    }
}
