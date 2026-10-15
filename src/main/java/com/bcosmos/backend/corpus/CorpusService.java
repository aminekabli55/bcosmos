package com.bcosmos.backend.corpus;

import com.bcosmos.backend.events.EventPublisher;
import com.bcosmos.backend.persistence.CommitRecordEntity;
import com.bcosmos.backend.persistence.CommitRecordJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusIngestResponse;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusRepositoryStatus;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusStatusResponse;
import com.bcosmos.backend.web.dto.CorpusResponses.IngestedRepository;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.events.MineRequestedPayload;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts mining and reports its progress (spec v2 section 8, {@code /api/corpus}).
 *
 * <p>Ingest does three things and refuses to blur them: it validates each URL against the allowlist, it makes sure a
 * {@code MINED} repository row exists (the worker and the status screen need one row per repository), and it asks
 * the miner to walk the repository. The request to the miner is published <em>after</em> the transaction commits, so
 * a rollback can never leave a message asking for a repository that does not exist.</p>
 *
 * <p>Status is read from Postgres, never from Kafka: an endpoint that has to ask a broker what happened is an
 * endpoint that answers differently depending on when it is called.</p>
 */
@Service
public class CorpusService {

    private static final Logger log = LoggerFactory.getLogger(CorpusService.class);

    private final RepositoryJpaRepository repositories;
    private final CommitRecordJpaRepository commits;
    private final EventPublisher events;
    private final CorpusProperties properties;
    private final Clock clock;

    public CorpusService(RepositoryJpaRepository repositories, CommitRecordJpaRepository commits,
            EventPublisher events, CorpusProperties properties, Clock clock) {
        this.repositories = repositories;
        this.commits = commits;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CorpusIngestResponse ingest(List<String> urls, Integer requestedWindow) {
        int commitWindow = requestedWindow == null || requestedWindow < 1 ? properties.commitWindow()
                : requestedWindow;
        List<IngestedRepository> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String url : urls == null ? List.<String>of() : urls) {
            String failure = refusalFor(url);
            if (failure == null && !seen.add(url)) {
                failure = "duplicated in this request";
            }
            if (failure != null) {
                rejected.add(url + ": " + failure);
                continue;
            }
            RepositoryEntity existing = repositories.findByRemoteUrl(url).orElse(null);
            boolean newlyRegistered = existing == null;
            RepositoryEntity repository = newlyRegistered
                    ? repositories.save(RepositoryEntity.mined(nameFor(url), url, null, null, clock.instant()))
                    : existing;
            accepted.add(new IngestedRepository(repository.getId(), repository.getName(), url, newlyRegistered));
            publishAfterCommit(repository.getId(), url, commitWindow);
        }
        return new CorpusIngestResponse(accepted, rejected, commitWindow);
    }

    @Transactional(readOnly = true)
    public CorpusStatusResponse status() {
        List<RepositoryEntity> mined = repositories.findAll().stream()
                .filter(repository -> repository.getSourceType() == RepositorySourceType.MINED)
                .toList();
        List<CorpusRepositoryStatus> rows = new ArrayList<>();
        long total = 0;
        for (RepositoryEntity repository : mined) {
            long recorded = commits.countByRepositoryId(repository.getId());
            total += recorded;
            CommitRecordEntity latest = commits.findFirstByRepositoryIdOrderByCommittedAtDesc(repository.getId())
                    .orElse(null);
            rows.add(new CorpusRepositoryStatus(repository.getId(), repository.getName(), repository.getRemoteUrl(),
                    repository.getStatus().name(), recorded,
                    latest == null ? null : latest.getHash(), latest == null ? null : latest.getCommittedAt()));
        }
        return new CorpusStatusResponse(mined.size(), total, rows);
    }

    /** {@code null} when the URL may be mined, otherwise why it may not. */
    private String refusalFor(String url) {
        if (url == null || url.isBlank()) {
            return "empty URL";
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return "not a valid URL";
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            // "not-a-url" parses as a valid relative URI, so scheme is the honest test for "is this a URL at all".
            return "not an absolute URL";
        }
        if (!"https".equals(uri.getScheme())) {
            return "only https URLs are mined";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!properties.allowedHosts().contains(host)) {
            return "host " + host + " is not in bcosmos.corpus.allowed-hosts";
        }
        return null;
    }

    /** The miner is asked only once the repository row is really there. */
    private void publishAfterCommit(long repositoryId, String url, int commitWindow) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                EventEnvelope<MineRequestedPayload> event = EventEnvelope.of(EventType.MINE_REQUESTED, repositoryId,
                        null, new MineRequestedPayload(url, null, commitWindow, repositoryId));
                events.publish(KafkaTopics.MINE_REQUESTED, url, event);
                log.info("asked the miner to walk a repository (id {}) for the last {} commits", repositoryId,
                        commitWindow);
            }
        });
    }

    /** The repository name the miner and the screens use: the last path segment, without a {@code .git} suffix. */
    static String nameFor(String url) {
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        int slash = trimmed.lastIndexOf('/');
        String name = slash < 0 ? trimmed : trimmed.substring(slash + 1);
        return name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
    }
}
