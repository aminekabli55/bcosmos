package com.bcosmos.backend.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.core.events.KafkaTopics;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 4 acceptance for corpus ingest (spec v2 sections 8 and 10): the allowlist is enforced before any event
 * exists, a repository row is created once per URL, the miner is asked exactly once per repository, and the status
 * endpoint answers from Postgres.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "bcosmos.kafka.enabled=true",
        "bcosmos.kafka.partitions=1",
        "bcosmos.corpus.allowed-hosts=github.com",
        "bcosmos.corpus.commit-window=50"
})
class CorpusIngestIntegrationTest {

    private static final String ALLOWED_URL = "https://github.com/apache/commons-lang";

    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("a URL outside the allowlist is rejected with its reason while the rest of the request proceeds")
    void enforcesTheAllowlist() throws Exception {
        JsonNode response = json.readTree(post("/api/corpus/ingest", """
                {"urls": ["%s", "https://gitlab.com/other/project", "http://github.com/apache/commons-lang",
                          "not-a-url", "%s"]}
                """.formatted(ALLOWED_URL, ALLOWED_URL)));

        assertThat(response.path("accepted")).hasSize(1);
        assertThat(response.path("accepted").get(0).path("remoteUrl").asString()).isEqualTo(ALLOWED_URL);
        assertThat(response.path("accepted").get(0).path("name").asString()).isEqualTo("commons-lang");
        assertThat(response.path("rejected")).hasSize(4);
        assertThat(response.path("rejected").toString())
                .contains("gitlab.com is not in bcosmos.corpus.allowed-hosts")
                .contains("only https URLs are mined")
                .contains("not an absolute URL")
                .contains("duplicated in this request");
        assertThat(response.path("commitWindow").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("a repository row is created once, has no local path, and the miner is asked once per ingest")
    void registersTheRepositoryOnceAndAsksTheMiner() throws Exception {
        post("/api/corpus/ingest", "{\"urls\": [\"" + ALLOWED_URL + "\"]}");
        JsonNode miner = json.readTree(post("/api/corpus/ingest", "{\"urls\": [\"" + ALLOWED_URL + "\"]}"));

        assertThat(miner.path("accepted").get(0).path("newlyRegistered").asBoolean()).isFalse();

        List<RepositoryEntity> mined = repositories.findAll().stream()
                .filter(repository -> repository.getSourceType() == RepositorySourceType.MINED)
                .toList();
        assertThat(mined).hasSize(1);
        RepositoryEntity repository = mined.getFirst();
        assertThat(repository.getRemoteUrl()).isEqualTo(ALLOWED_URL);
        // A mined repository lives in the miner's cache: the backend owns no path for it (changelog 0016).
        assertThat(repository.getCanonicalLocalPath()).isNull();

        List<String> keyed = awaitMineRequests(2);
        assertThat(keyed).hasSize(2);
        assertThat(keyed).allSatisfy(key -> assertThat(key).isEqualTo(ALLOWED_URL));

        JsonNode status = json.readTree(get("/api/corpus/status"));
        assertThat(status.path("repositories").asLong()).isEqualTo(1);
        assertThat(status.path("commitsRecorded").asLong()).isZero();
        assertThat(status.path("items").get(0).path("latestCommitHash").isNull()).isTrue();
    }

    /** Polls the mine topic until {@code count} records arrived, so the test does not depend on a fixed sleep. */
    private List<String> awaitMineRequests(int count) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "corpus-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(KafkaTopics.MINE_REQUESTED));
            java.util.List<String> keys = new java.util.ArrayList<>();
            Instant deadline = Instant.now().plus(RECEIVE_TIMEOUT);
            while (keys.size() < count && Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(record -> keys.add(record.key()));
            }
            return keys;
        }
    }

    private String post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private String get(String path) throws Exception {
        return send(HttpRequest.newBuilder(URI.create(base() + path)).GET());
    }

    private String send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(200);
        return response.body();
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
