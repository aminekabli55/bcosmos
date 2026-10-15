package com.bcosmos.miner.consumer;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.events.MineRequestedPayload;
import com.bcosmos.miner.config.MinerProperties;
import com.bcosmos.miner.git.CommitWalker;
import com.bcosmos.miner.git.JGitCloneCache;
import java.net.URI;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code repo.mine.requested.v1} to clone and walk repositories (spec v2 section 5.1, section 10).
 */
@Component
public class MineRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(MineRequestedConsumer.class);

    private final JGitCloneCache cloneCache;
    private final CommitWalker commitWalker;
    private final MinerProperties properties;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final JavaType envelopeType;

    public MineRequestedConsumer(
            JGitCloneCache cloneCache,
            CommitWalker commitWalker,
            MinerProperties properties,
            KafkaTemplate<String, String> kafka,
            ObjectMapper json) {
        this.cloneCache = cloneCache;
        this.commitWalker = commitWalker;
        this.properties = properties;
        this.kafka = kafka;
        this.json = json;
        this.envelopeType = json.getTypeFactory().constructParametricType(
                EventEnvelope.class, MineRequestedPayload.class);
    }

    @KafkaListener(topics = KafkaTopics.MINE_REQUESTED, groupId = "${spring.kafka.consumer.group-id:bcosmos-miner}")
    public void consume(String message) {
        EventEnvelope<MineRequestedPayload> envelope;
        try {
            envelope = json.readValue(message, envelopeType);
        } catch (Exception e) {
            log.error("Failed to deserialize MineRequested message: {}", e.getMessage());
            throw new IllegalArgumentException("Corrupt MineRequested message", e);
        }

        if (!envelope.understandsSchema()) {
            throw new IllegalArgumentException("Unsupported schema version: " + envelope.schemaVersion());
        }

        MineRequestedPayload payload = envelope.payload();
        String remoteUrl = payload.remoteUrl();

        URI uri;
        try {
            uri = URI.create(remoteUrl);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid remote URL: " + remoteUrl, e);
        }

        String host = uri.getHost();
        if (!properties.isHostAllowed(host)) {
            log.warn("Refusing to mine repository from unallowed host: {}", host);
            throw new IllegalArgumentException("Host not allowed: " + host);
        }

        Long repositoryId = payload.repositoryId() != null ? payload.repositoryId() : envelope.repositoryId();
        if (repositoryId == null) {
            throw new IllegalArgumentException("repositoryId is required");
        }

        int commitWindow = payload.commitWindow() > 0 ? payload.commitWindow() : properties.commitWindow();

        try (Repository repo = cloneCache.openOrClone(repositoryId, remoteUrl)) {
            List<CommitDiscoveredPayload> commits = commitWalker.walkCommits(repo, commitWindow);
            log.info("Discovered {} commits with Java changes in repository {}", commits.size(), repositoryId);

            for (CommitDiscoveredPayload commit : commits) {
                EventEnvelope<CommitDiscoveredPayload> discoveredEnvelope = EventEnvelope.of(
                        EventType.COMMIT_DISCOVERED,
                        repositoryId,
                        envelope.correlationId(),
                        commit);

                String key = String.valueOf(repositoryId);
                String value = json.writeValueAsString(discoveredEnvelope);
                kafka.send(KafkaTopics.COMMIT_DISCOVERED, key, value);
            }
        } catch (Exception e) {
            log.error("Failed mining repository {} from {}: {}", repositoryId, remoteUrl, e.getMessage(), e);
            throw new RuntimeException("Error mining repository " + repositoryId, e);
        }
    }
}
