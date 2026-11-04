package com.bcosmos.core.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    @DisplayName("an envelope gets a fresh event id, the current schema version and a correlation id of its own")
    void fillsInTheEnvelope() {
        EventEnvelope<CommitDiscoveredPayload> event = EventEnvelope.of(EventType.COMMIT_DISCOVERED, 42L, null,
                new CommitDiscoveredPayload("abc123", List.of("def456"), Instant.parse("2026-10-14T10:00:00Z"),
                        List.of("src/main/java/com/acme/A.java")));

        assertThat(event.eventId()).isNotBlank();
        assertThat(event.schemaVersion()).isEqualTo(EventEnvelope.SCHEMA_VERSION);
        assertThat(event.eventType()).isEqualTo(EventType.COMMIT_DISCOVERED);
        assertThat(event.repositoryId()).isEqualTo(42L);
        assertThat(event.correlationId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.understandsSchema()).isTrue();
    }

    @Test
    @DisplayName("the caller's correlation id is kept, so the messages of one request can be tied together")
    void keepsTheGivenCorrelationId() {
        EventEnvelope<AnalysisRequestedPayload> event = EventEnvelope.of(EventType.ANALYSIS_REQUESTED, 7L, "c-1",
                new AnalysisRequestedPayload(9L, "/repos/demo", "java"));

        assertThat(event.correlationId()).isEqualTo("c-1");
        assertThat(event.withCorrelation("c-2").correlationId()).isEqualTo("c-2");
        assertThat(event.withCorrelation("c-2").eventId()).isEqualTo(event.eventId());
    }

    @Test
    @DisplayName("a consumer can refuse a schema version it does not understand")
    void reportsAnUnknownSchemaVersion() {
        EventEnvelope<String> future = new EventEnvelope<>("e-1", EventType.FINDINGS, 99, Instant.now(), "c-1", 1L,
                "payload");

        assertThat(future.understandsSchema()).isFalse();
    }

    @Test
    @DisplayName("an envelope without an id, type or payload is refused rather than sent")
    void refusesAnIncompleteEnvelope() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> new EventEnvelope<>("", EventType.FINDINGS, 1, now, null, null, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        assertThatThrownBy(() -> new EventEnvelope<>("e-1", null, 1, now, null, null, "payload"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventEnvelope<>("e-1", EventType.FINDINGS, 1, now, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventEnvelope<>("e-1", EventType.FINDINGS, 0, now, null, null, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    @DisplayName("a commit event without a changed Java file is not worth publishing")
    void refusesACommitWithNoChangedFiles() {
        assertThatThrownBy(() -> new CommitDiscoveredPayload("abc123", List.of(), Instant.now(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changed Java file");
    }

    @Test
    @DisplayName("a findings payload refuses an absolute path, which must never reach the wire")
    void refusesAnAbsoluteFilePath() {
        assertThatThrownBy(() -> new FindingsPayload("abc123", null, "C:/repos/demo/A.java", "h1", 10,
                FindingsPayload.Side.AFTER, "7.28.0", "hash", "OK", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository-relative");
    }

    @Test
    @DisplayName("a findings payload carries the analysed line count and refuses a negative one")
    void carriesTheLineCount() {
        FindingsPayload payload = new FindingsPayload("abc123", null, "src/A.java", "h1", 42,
                FindingsPayload.Side.AFTER, "7.28.0", "hash", "OK", List.of());
        assertThat(payload.lineCount()).isEqualTo(42);

        assertThatThrownBy(() -> new FindingsPayload("abc123", null, "src/A.java", "h1", -1,
                FindingsPayload.Side.AFTER, "7.28.0", "hash", "OK", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineCount");
    }

    @Test
    @DisplayName("the dead-letter topic of a topic is its name plus the documented suffix")
    void namesTheDeadLetterTopic() {
        assertThat(KafkaTopics.deadLetterOf(KafkaTopics.FINDINGS)).isEqualTo("analysis.findings.v1.dlq.v1");
        assertThat(KafkaTopics.ANALYSIS_REQUESTED).isEqualTo("repo.analysis.requested.v1");
        assertThat(KafkaTopics.COMMIT_DISCOVERED).isEqualTo("repo.commit.discovered.v1");
        assertThat(KafkaTopics.MINE_REQUESTED).isEqualTo("repo.mine.requested.v1");
    }
}
