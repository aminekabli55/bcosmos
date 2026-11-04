package com.bcosmos.backend.web.dto;

import java.time.Instant;
import java.util.List;

/** Bodies and responses of {@code /api/corpus} (spec v2 section 8). DTOs only: entities never leave the service. */
public final class CorpusResponses {

    private CorpusResponses() {
    }

    /**
     * Body of {@code POST /api/corpus/ingest}.
     *
     * @param urls         public HTTPS repository URLs to mine
     * @param commitWindow how many commits back to walk; {@code null} uses the configured default
     */
    public record CorpusIngestRequest(List<String> urls, Integer commitWindow) {
    }

    /** One repository a request asked for. */
    public record IngestedRepository(long repositoryId, String name, String remoteUrl, boolean newlyRegistered) {
    }

    /**
     * What happened to one ingest request. A rejected URL is reported with its reason and the rest of the request
     * still proceeds: an operator fixing a typo should not have to resubmit the twelve URLs that were fine.
     */
    public record CorpusIngestResponse(List<IngestedRepository> accepted, List<String> rejected, int commitWindow) {
    }

    /** Progress of one mined repository, read from Postgres rather than from Kafka (spec v2 section 8). */
    public record CorpusRepositoryStatus(long repositoryId, String name, String remoteUrl, String status,
            long commitsRecorded, String latestCommitHash, Instant latestCommitAt) {
    }

    /** {@code GET /api/corpus/status}. */
    public record CorpusStatusResponse(long repositories, long commitsRecorded, List<CorpusRepositoryStatus> items) {
    }
}
