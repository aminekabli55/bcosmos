package com.bcosmos.miner.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.events.MineRequestedPayload;
import com.bcosmos.miner.config.MinerProperties;
import com.bcosmos.miner.git.CommitWalker;
import com.bcosmos.miner.git.JGitCloneCache;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

@DisplayName("MineRequestedConsumer unit tests (ADR 0023, ADR 0026)")
class MineRequestedConsumerTest {

    private JGitCloneCache cloneCache;
    private CommitWalker commitWalker;
    private MinerProperties properties;
    private KafkaTemplate<String, String> kafka;
    private ObjectMapper json;
    private MineRequestedConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        cloneCache = mock(JGitCloneCache.class);
        commitWalker = mock(CommitWalker.class);
        properties = new MinerProperties("github.com,gitlab.com", "data/repos", 50, 3, 500, 2.0);
        kafka = mock(KafkaTemplate.class);
        json = new ObjectMapper();
        consumer = new MineRequestedConsumer(cloneCache, commitWalker, properties, kafka, json);
    }

    @Test
    void refusesUnallowedHostImmediately() throws Exception {
        MineRequestedPayload payload = new MineRequestedPayload("https://evil.attacker.com/repo.git", "main", 10, 42L);
        EventEnvelope<MineRequestedPayload> envelope = EventEnvelope.of(
                EventType.MINE_REQUESTED, 42L, "corr-1", payload);
        String message = json.writeValueAsString(envelope);

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Host not allowed: evil.attacker.com");
    }

    @Test
    void clonesAndPublishesDiscoveredCommitsForAllowedHost() throws Exception {
        Repository mockRepo = mock(Repository.class);
        when(cloneCache.openOrClone(anyLong(), anyString())).thenReturn(mockRepo);

        CommitDiscoveredPayload commit = new CommitDiscoveredPayload(
                "abc12345", List.of(), Instant.now(), List.of("src/App.java"));
        when(commitWalker.walkCommits(eq(mockRepo), anyInt())).thenReturn(List.of(commit));

        MineRequestedPayload payload = new MineRequestedPayload("https://github.com/sample/repo.git", "main", 10, 55L);
        EventEnvelope<MineRequestedPayload> envelope = EventEnvelope.of(
                EventType.MINE_REQUESTED, 55L, "corr-2", payload);
        String message = json.writeValueAsString(envelope);

        consumer.consume(message);

        verify(cloneCache).openOrClone(55L, "https://github.com/sample/repo.git");
        verify(kafka).send(eq(KafkaTopics.COMMIT_DISCOVERED), eq("55"), anyString());
    }
}
