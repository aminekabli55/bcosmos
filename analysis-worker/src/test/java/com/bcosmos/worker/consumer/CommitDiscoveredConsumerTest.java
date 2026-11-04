package com.bcosmos.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.FindingsPayload;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.pipeline.CommitAnalysisPipeline;
import com.bcosmos.worker.config.WorkerProperties;
import com.bcosmos.worker.events.FindingsPublisher;
import com.bcosmos.worker.persistence.WorkerPersistenceService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

@DisplayName("CommitDiscoveredConsumer unit tests (ADR 0011, ADR 0027)")
class CommitDiscoveredConsumerTest {

    @TempDir
    Path tempDir;

    private WorkerPersistenceService persistence;
    private CommitAnalysisPipeline pipeline;
    private FindingsPublisher publisher;
    private WorkerProperties properties;
    private ObjectMapper json;
    private CommitDiscoveredConsumer consumer;

    private String commitHash;
    private String javaFilePath = "src/main/java/com/example/Sample.java";

    @BeforeEach
    void setUp() throws Exception {
        // Initialize real git repo in tempDir
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Path file = tempDir.resolve(javaFilePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    package com.example;
                    public class Sample {
                        public void test() {
                            try {
                                System.out.println("hello");
                            } catch (Exception e) {
                            }
                        }
                    }
                    """);
            git.add().addFilepattern("src/main/java/com/example/Sample.java").call();
            RevCommit commit = git.commit().setMessage("initial").call();
            commitHash = commit.getName();
        }

        persistence = mock(WorkerPersistenceService.class);
        pipeline = new CommitAnalysisPipeline(new JavaAdapter());
        publisher = mock(FindingsPublisher.class);
        // Point cloneDir directly to tempDir
        properties = new WorkerProperties(3, 500, 2.0, tempDir.toString());
        json = new ObjectMapper();
        consumer = new CommitDiscoveredConsumer(persistence, pipeline, publisher, properties, json);
    }

    @Test
    void processesCommitBlobsWithoutCheckoutAndPublishesFindings() throws Exception {
        CommitDiscoveredPayload payload = new CommitDiscoveredPayload(
                commitHash, List.of(), Instant.now(), List.of(javaFilePath));
        EventEnvelope<CommitDiscoveredPayload> envelope = EventEnvelope.of(
                EventType.COMMIT_DISCOVERED, 1L, "corr-commit-1", payload);
        String message = json.writeValueAsString(envelope);

        consumer.consume(message);

        verify(persistence).saveCommitRecord(eq(1L), eq(commitHash), eq(List.of()), any(), eq(1));
        verify(persistence).recordFileResult(
                eq(1L), eq(commitHash), eq(javaFilePath), eq("AFTER"),
                anyString(), anyString(), eq("OK"), anyInt());

        verify(publisher).publish(argThat(env -> {
            FindingsPayload p = env.payload();
            return p.commitHash().equals(commitHash)
                    && p.filePath().equals(javaFilePath)
                    && p.side() == FindingsPayload.Side.AFTER
                    && !p.findings().isEmpty();
        }));
    }

    @Test
    void skipsFileWhenAlreadyProcessedInIdempotencyGate() throws Exception {
        when(persistence.isFileResultProcessed(eq(1L), eq(commitHash), eq(javaFilePath), eq("AFTER"), anyString(), anyString()))
                .thenReturn(true);

        CommitDiscoveredPayload payload = new CommitDiscoveredPayload(
                commitHash, List.of(), Instant.now(), List.of(javaFilePath));
        EventEnvelope<CommitDiscoveredPayload> envelope = EventEnvelope.of(
                EventType.COMMIT_DISCOVERED, 1L, "corr-commit-2", payload);
        String message = json.writeValueAsString(envelope);

        consumer.consume(message);

        verify(persistence).saveCommitRecord(eq(1L), eq(commitHash), eq(List.of()), any(), eq(1));
        // Idempotency check passed, so no analysis or publish occurs
        verify(publisher, never()).publish(any());
    }

    @Test
    void throwsOnCorruptJson() {
        assertThatThrownBy(() -> consumer.consume("invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Corrupt CommitDiscovered message");
    }
}
