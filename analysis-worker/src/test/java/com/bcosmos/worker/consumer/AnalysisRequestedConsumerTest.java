package com.bcosmos.worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcosmos.core.events.AnalysisRequestedPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.pipeline.CommitAnalysisPipeline;
import com.bcosmos.worker.persistence.WorkerAnalysisRunEntity;
import com.bcosmos.worker.persistence.WorkerPersistenceService;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("AnalysisRequestedConsumer unit tests")
class AnalysisRequestedConsumerTest {

    private WorkerPersistenceService persistence;
    private CommitAnalysisPipeline pipeline;
    private ObjectMapper json;
    private AnalysisRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        persistence = mock(WorkerPersistenceService.class);
        pipeline = new CommitAnalysisPipeline(new JavaAdapter());
        json = new ObjectMapper();
        consumer = new AnalysisRequestedConsumer(persistence, pipeline, json);
    }

    @Test
    void analysesRegisteredLocalDirectorySuccessfully() throws Exception {
        Path fixturePath = Path.of("../sample-data/java-fixture").toAbsolutePath().normalize();
        if (!fixturePath.toFile().exists()) {
            fixturePath = Path.of("sample-data/java-fixture").toAbsolutePath().normalize();
        }

        AnalysisRequestedPayload payload = new AnalysisRequestedPayload(100L, fixturePath.toString(), "java");
        EventEnvelope<AnalysisRequestedPayload> envelope = EventEnvelope.of(
                EventType.ANALYSIS_REQUESTED, 1L, "corr-1", payload);
        String message = json.writeValueAsString(envelope);

        when(persistence.findRun(100L)).thenReturn(Optional.of(
                new WorkerAnalysisRunEntity(1L, "QUEUED", "PENDING", 1, Instant.now())));

        consumer.consume(message);

        verify(persistence).startRun(eq(100L), eq(7));
        verify(persistence, times(7)).saveFileAnalysis(
                eq(100L), anyString(), eq("java"), any(), anyInt(), anyString(), any(), any());
        verify(persistence).completeRun(eq(100L), eq(6), eq(1), anyString(), anyString(), anyString());
    }

    @Test
    void ignoresDuplicateIfRunAlreadySucceeded() throws Exception {
        Path fixturePath = Path.of("../sample-data/java-fixture").toAbsolutePath().normalize();
        AnalysisRequestedPayload payload = new AnalysisRequestedPayload(200L, fixturePath.toString(), "java");
        EventEnvelope<AnalysisRequestedPayload> envelope = EventEnvelope.of(
                EventType.ANALYSIS_REQUESTED, 1L, "corr-2", payload);
        String message = json.writeValueAsString(envelope);

        WorkerAnalysisRunEntity completedRun = new WorkerAnalysisRunEntity(1L, "SUCCEEDED", "COMPLETED", 1, Instant.now());
        when(persistence.findRun(200L)).thenReturn(Optional.of(completedRun));

        consumer.consume(message);

        verify(persistence, never()).startRun(anyLong(), anyInt());
        verify(persistence, never()).saveFileAnalysis(anyLong(), anyString(), anyString(), any(), anyInt(), anyString(), any(), any());
    }

    @Test
    void throwsOnCorruptJson() {
        assertThatThrownBy(() -> consumer.consume("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Corrupt AnalysisRequested message");
    }

    @Test
    void throwsOnMissingDirectory() throws Exception {
        AnalysisRequestedPayload payload = new AnalysisRequestedPayload(300L, "non-existent-dir-12345", "java");
        EventEnvelope<AnalysisRequestedPayload> envelope = EventEnvelope.of(
                EventType.ANALYSIS_REQUESTED, 1L, "corr-3", payload);
        String message = json.writeValueAsString(envelope);

        when(persistence.findRun(300L)).thenReturn(Optional.of(
                new WorkerAnalysisRunEntity(1L, "QUEUED", "PENDING", 1, Instant.now())));

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Repository path not found");

        verify(persistence).failRun(eq(300L), eq("PATH_NOT_FOUND"), anyString());
    }
}
