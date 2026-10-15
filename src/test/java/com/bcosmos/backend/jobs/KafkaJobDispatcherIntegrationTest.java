package com.bcosmos.backend.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.KafkaTopics;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 4 acceptance for the dispatch seam: with {@code bcosmos.kafka.enabled=true} the in-process runner is gone,
 * and starting a run puts one real message on {@code repo.analysis.requested.v1} carrying what the worker needs
 * (spec v2 sections 5.1 and 5.3).
 *
 * <p>It runs against the real Kafka container the other integration tests already start, because the part worth
 * testing here is the wire: the topic name, the partition key and the JSON the worker will parse.</p>
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "bcosmos.kafka.enabled=true",
        "bcosmos.kafka.partitions=1",
        "bcosmos.kafka.replicas=1"
})
class KafkaJobDispatcherIntegrationTest {

    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AnalysisRunService runService;

    @Autowired
    private AnalysisRunJpaRepository runs;

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("only the Kafka dispatcher exists when the pipeline is on, so nothing is executed in-process")
    void theInProcessDispatcherIsReplaced() {
        assertThat(context.getBeanNamesForType(JobDispatcher.class))
                .containsExactlyInAnyOrder("kafkaJobDispatcher");
    }

    @Test
    @DisplayName("starting a run publishes its request, keyed by repository, and leaves it pending for the worker")
    void publishesTheRunToKafka() throws Exception {
        RepositoryEntity repository = repositories.save(new RepositoryEntity("pipeline-fixture",
                "/tmp/pipeline-fixture", RepositorySourceType.LOCAL, "java", RepositoryStatus.REGISTERED,
                Instant.now()));
        AnalysisRunEntity run = runService.start(repository.getId());

        ConsumerRecord<String, String> record = awaitOne();

        assertThat(record.topic()).isEqualTo(KafkaTopics.ANALYSIS_REQUESTED);
        assertThat(record.key()).isEqualTo(String.valueOf(repository.getId()));

        JsonNode event = json.readTree(record.value());
        assertThat(event.path("eventType").asString()).isEqualTo(EventType.ANALYSIS_REQUESTED.name());
        assertThat(event.path("schemaVersion").asInt()).isEqualTo(EventEnvelope.SCHEMA_VERSION);
        assertThat(event.path("repositoryId").asLong()).isEqualTo(repository.getId());
        assertThat(event.path("eventId").asString()).isNotBlank();
        assertThat(event.path("correlationId").asString()).isNotBlank();
        // The worker needs the path, and it travels on the broker only: it is not part of the run the API returns.
        assertThat(event.path("payload").path("analysisRunId").asLong()).isEqualTo(run.getId());
        assertThat(event.path("payload").path("localPath").asString()).isEqualTo("/tmp/pipeline-fixture");
        assertThat(event.path("payload").path("primaryLanguage").asString()).isEqualTo("java");

        // No worker is running in this test, so the run must still be waiting: the backend did not execute it.
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.PENDING);
    }

    /** Polls the topic until one record arrives, so the test does not depend on a fixed sleep. */
    private ConsumerRecord<String, String> awaitOne() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dispatcher-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(KafkaTopics.ANALYSIS_REQUESTED));
            Instant deadline = Instant.now().plus(RECEIVE_TIMEOUT);
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
            throw new AssertionError("no event arrived on " + KafkaTopics.ANALYSIS_REQUESTED + " within "
                    + RECEIVE_TIMEOUT);
        }
    }
}
