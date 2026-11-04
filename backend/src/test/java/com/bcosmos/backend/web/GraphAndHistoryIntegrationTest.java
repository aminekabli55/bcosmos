package com.bcosmos.backend.web;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.RelationshipEntity;
import com.bcosmos.backend.persistence.RelationshipJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Code Cosmos Graph & History Comparison API (Phase 8, spec section 8 & 9)")
class GraphAndHistoryIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private AnalysisRunJpaRepository runs;

    @Autowired
    private FileRecordJpaRepository files;

    @Autowired
    private SymbolJpaRepository symbols;

    @Autowired
    private RelationshipJpaRepository relationships;

    @Autowired
    private FindingJpaRepository findings;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long repoId;
    private Long run1Id;
    private Long run2Id;
    private Long symbol1Id;
    private Long symbol2Id;

    @BeforeEach
    void setUp() {
        jdbc.execute("SELECT setval('repository_id_seq', COALESCE((SELECT MAX(id) FROM repository), 0) + 1, false)");
        jdbc.execute("SELECT setval('analysis_run_id_seq', COALESCE((SELECT MAX(id) FROM analysis_run), 0) + 1, false)");
        jdbc.execute("SELECT setval('file_record_id_seq', COALESCE((SELECT MAX(id) FROM file_record), 0) + 1, false)");
        jdbc.execute("SELECT setval('symbol_id_seq', COALESCE((SELECT MAX(id) FROM symbol), 0) + 1, false)");
        jdbc.execute("SELECT setval('finding_id_seq', COALESCE((SELECT MAX(id) FROM finding), 0) + 1, false)");
        jdbc.execute("SELECT setval('relationship_id_seq', COALESCE((SELECT MAX(id) FROM relationship), 0) + 1, false)");
        jdbc.execute("SELECT setval('finding_transition_id_seq', COALESCE((SELECT MAX(id) FROM finding_transition), 0) + 1, false)");

        long id = System.nanoTime();
        RepositoryEntity repo = repositories.save(new RepositoryEntity(
                "graph-fixture-" + id,
                "c:/dummy/path/" + id,
                RepositorySourceType.LOCAL,
                "java",
                RepositoryStatus.REGISTERED,
                Instant.now()));
        repoId = repo.getId();

        AnalysisRunEntity run1 = runs.save(new AnalysisRunEntity(
                repoId, AnalysisRunStatus.SUCCEEDED, AnalysisStage.COMPLETED, 1, Instant.now()));
        run1Id = run1.getId();

        AnalysisRunEntity run2 = runs.save(new AnalysisRunEntity(
                repoId, AnalysisRunStatus.SUCCEEDED, AnalysisStage.COMPLETED, 1, Instant.now()));
        run2Id = run2.getId();

        FileRecordEntity file1 = files.save(new FileRecordEntity(
                run1Id, "Service.java", "java", "hash1", 40, ParseStatus.OK, null));
        FileRecordEntity file2 = files.save(new FileRecordEntity(
                run2Id, "Service.java", "java", "hash2", 45, ParseStatus.OK, null));

        SymbolEntity s1 = symbols.save(new SymbolEntity(
                run1Id, file1.getId(), SymbolKind.CLASS, "MyService", "com.acme.MyService",
                null, new Span(1, 1, 30, 1), "sig1"));
        symbol1Id = s1.getId();

        SymbolEntity s2 = symbols.save(new SymbolEntity(
                run1Id, file1.getId(), SymbolKind.CLASS, "MyRepo", "com.acme.MyRepo",
                null, new Span(1, 1, 20, 1), "sig2"));
        symbol2Id = s2.getId();

        relationships.save(new RelationshipEntity(
                run1Id, symbol1Id, symbol2Id, "MyRepo", RelationshipType.USES_TYPE,
                ResolutionStatus.RESOLVED, 1.0, "Service.java", 5));

        findings.save(new FindingEntity(
                run1Id, file1.getId(), "pmd", "7.0", "EmptyCatchBlock", "HIGH", Severity.HIGH,
                "empty catch", new Span(10, 1, 12, 1), "com.acme.MyService", "catch(e){}", "catch(e){}",
                "fp-common-1", TriageStatus.OPEN, Instant.now()));

        findings.save(new FindingEntity(
                run2Id, file2.getId(), "pmd", "7.0", "EmptyCatchBlock", "HIGH", Severity.HIGH,
                "empty catch", new Span(10, 1, 12, 1), "com.acme.MyService", "catch(e){}", "catch(e){}",
                "fp-common-1", TriageStatus.OPEN, Instant.now()));
    }

    @Test
    void getGraphReturnsNodesAndEdges() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/analyses/" + run1Id + "/graph?maxNodes=50"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("analysisId")).longValue()).isEqualTo(run1Id);
        assertThat((Iterable<?>) body.get("nodes")).hasSize(2);
        assertThat((Iterable<?>) body.get("edges")).hasSize(1);
    }

    @Test
    void getHotspotsReturnsRankedSymbols() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/analyses/" + run1Id + "/hotspots"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        assertThat(body).isNotNull();
        assertThat((Iterable<?>) body.get("items")).isNotEmpty();
        assertThat(body.get("heuristicFormula").toString()).contains("score =");
    }

    @Test
    void compareSnapshotsCalculatesTransitions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/analyses/compare?from=" + run1Id + "&to=" + run2Id))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("persistentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("newCount")).intValue()).isEqualTo(0);
        assertThat(((Number) body.get("resolvedCount")).intValue()).isEqualTo(0);
    }
}
