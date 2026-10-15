package com.bcosmos.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code DELETE /api/repositories/{id}} against the real changelog (ADR 0037).
 *
 * <p>The point of running this against a database rather than mocks is the cascade: the service deletes one row and
 * PostgreSQL removes the rest, and that only holds if every child foreign key really says {@code ON DELETE CASCADE}
 * (changelog 0024 added the last one). A mocked repository would happily pretend that works.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Deleting a repository (ADR 0037)")
class RepositoryDeletionIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private AnalysisRunJpaRepository runs;

    @Autowired
    private FileRecordJpaRepository files;

    @Autowired
    private SymbolJpaRepository symbols;

    @Autowired
    private FindingJpaRepository findings;

    @Autowired
    private JdbcTemplate jdbc;

    private Long repositoryId;
    private Long runId;

    @BeforeEach
    void setUp() {
        jdbc.execute("SELECT setval('repository_id_seq', COALESCE((SELECT MAX(id) FROM repository), 0) + 1, false)");
        jdbc.execute("SELECT setval('analysis_run_id_seq', COALESCE((SELECT MAX(id) FROM analysis_run), 0) + 1, false)");
    }

    /** A repository with one finished run and one row in every table that hangs off it. */
    private void givenARepositoryWithFindings() {
        String unique = "delete-fixture-" + System.nanoTime();
        RepositoryEntity repository = repositories.save(new RepositoryEntity(
                unique, "c:/dummy/path/" + unique, RepositorySourceType.LOCAL, "java",
                RepositoryStatus.ANALYSED, Instant.now()));
        repositoryId = repository.getId();

        AnalysisRunEntity run = runs.save(new AnalysisRunEntity(
                repositoryId, AnalysisRunStatus.SUCCEEDED, AnalysisStage.COMPLETED, 1, Instant.now()));
        runId = run.getId();

        FileRecordEntity file = files.save(new FileRecordEntity(
                runId, "IngestRunner.java", "java", "hash", 40, ParseStatus.OK, null));

        symbols.save(new SymbolEntity(runId, file.getId(), SymbolKind.CLASS, "IngestRunner",
                "com.example.catalog.IngestRunner", null, new Span(1, 1, 30, 1), "sig"));

        findings.save(new FindingEntity(runId, file.getId(), "pmd", "7.28.0", "EmptyCatchBlock", "MEDIUM",
                Severity.MEDIUM, "an empty catch block", new Span(22, 1, 24, 1), "com.example.catalog.IngestRunner",
                "catch (Exception e) {}", "catch(e){}", "fp-delete-1", TriageStatus.OPEN, Instant.now()));

        // commit_review is the one repository child changeset 0024 had to fix: before it, this row blocked the
        // delete with a foreign-key error and the whole feature was impossible.
        jdbc.update("INSERT INTO commit_review (repository_id, from_commit_hash, to_commit_hash, created_at)"
                + " VALUES (?, ?, ?, now())", repositoryId, "a".repeat(40), "b".repeat(40));
    }

    private HttpResponse<String> deleteRepository(long id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/repositories/" + id))
                .DELETE()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getRepository(long id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/repositories/" + id))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long count(String table, String where, Object... args) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Long.class, args);
        return count == null ? 0 : count;
    }

    @Test
    void deletingARepositoryRemovesItsRunsAndEverythingDerivedFromThem() throws Exception {
        givenARepositoryWithFindings();

        HttpResponse<String> response = deleteRepository(repositoryId);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();

        assertThat(getRepository(repositoryId).statusCode()).isEqualTo(404);
        assertThat(count("analysis_run", "repository_id = ?", repositoryId)).isZero();
        assertThat(count("file_record", "run_id = ?", runId)).isZero();
        assertThat(count("symbol", "run_id = ?", runId)).isZero();
        assertThat(count("finding", "run_id = ?", runId)).isZero();
        assertThat(count("commit_review", "repository_id = ?", repositoryId)).isZero();
    }

    @Test
    void aRepositoryThatIsAlreadyGoneIsNotFound() throws Exception {
        HttpResponse<String> response = deleteRepository(987_654_321L);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Repository not found");
    }

    @Test
    void anAnalysisStillInFlightBlocksTheDelete() throws Exception {
        String unique = "busy-fixture-" + System.nanoTime();
        RepositoryEntity repository = repositories.save(new RepositoryEntity(
                unique, "c:/dummy/path/" + unique, RepositorySourceType.LOCAL, "java",
                RepositoryStatus.REGISTERED, Instant.now()));
        long id = repository.getId();
        runs.save(new AnalysisRunEntity(id, AnalysisRunStatus.RUNNING, AnalysisStage.PARSING, 1, Instant.now()));

        HttpResponse<String> response = deleteRepository(id);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("has not finished yet");
        // The refusal is real: the repository is still there for the runner to finish writing against.
        assertThat(getRepository(id).statusCode()).isEqualTo(200);
    }
}
