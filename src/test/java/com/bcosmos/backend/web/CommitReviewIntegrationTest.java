package com.bcosmos.backend.web;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.persistence.CommitRecordEntity;
import com.bcosmos.backend.persistence.CommitRecordJpaRepository;
import com.bcosmos.backend.persistence.CommitReviewEntity;
import com.bcosmos.backend.persistence.CommitReviewJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.backend.web.dto.CommitResponses.CommitListResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CommitReviewResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CreateCommitReviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Commit Review REST API (Phase 9, spec section 7.6 & 8)")
class CommitReviewIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RepositoryJpaRepository repositories;

    @Autowired
    private CommitRecordJpaRepository commitRecords;

    @Autowired
    private CommitReviewJpaRepository commitReviews;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long repoId;

    @BeforeEach
    void setUp() {
        jdbc.execute("SELECT setval('repository_id_seq', COALESCE((SELECT MAX(id) FROM repository), 0) + 1, false)");
        jdbc.execute("SELECT setval('commit_record_id_seq', COALESCE((SELECT MAX(id) FROM commit_record), 0) + 1, false)");
        jdbc.execute("SELECT setval('commit_review_id_seq', COALESCE((SELECT MAX(id) FROM commit_review), 0) + 1, false)");

        long id = System.nanoTime();
        RepositoryEntity repo = repositories.save(new RepositoryEntity(
                "commit-fixture-" + id,
                "c:/dummy/commit-path/" + id,
                RepositorySourceType.LOCAL,
                "java",
                RepositoryStatus.REGISTERED,
                Instant.now()));
        repoId = repo.getId();

        // Seed some commit records for the repository
        commitRecords.save(new CommitRecordEntity(
                repoId, "commit-hash-1111111111111111111111111111111111111111",
                new String[]{}, Instant.now().minusSeconds(3600), "author1", 2, Instant.now()));
        commitRecords.save(new CommitRecordEntity(
                repoId, "commit-hash-2222222222222222222222222222222222222222",
                new String[]{"commit-hash-1111111111111111111111111111111111111111"}, Instant.now(), "author2", 1, Instant.now()));
    }

    @Test
    @DisplayName("GET /api/repositories/{id}/commits lists commits for repository")
    void listCommitsReturnsRecentCommits() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/repositories/" + repoId + "/commits"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);

        CommitListResponse body = objectMapper.readValue(resp.body(), CommitListResponse.class);
        assertThat(body.repositoryId()).isEqualTo(repoId);
        assertThat(body.commits()).hasSize(2);
        assertThat(body.commits().get(0).hash()).isEqualTo("commit-hash-2222222222222222222222222222222222222222");
    }

    @Test
    @DisplayName("POST /api/repositories/{id}/commit-reviews creates a 3-layer review")
    void createAndGetCommitReview() throws Exception {
        CreateCommitReviewRequest createReq = new CreateCommitReviewRequest(
                "commit-hash-1111111111111111111111111111111111111111",
                "commit-hash-2222222222222222222222222222222222222222",
                null);

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/repositories/" + repoId + "/commit-reviews"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(createReq)))
                .build();

        HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertThat(postResp.statusCode()).isEqualTo(201);

        CommitReviewResponse created = objectMapper.readValue(postResp.body(), CommitReviewResponse.class);
        assertThat(created.id()).isNotNull();
        assertThat(created.repositoryId()).isEqualTo(repoId);
        assertThat(created.fromCommit()).isEqualTo("commit-hash-1111111111111111111111111111111111111111");
        assertThat(created.toCommit()).isEqualTo("commit-hash-2222222222222222222222222222222222222222");
        assertThat(created.facts()).isNotNull();
        assertThat(created.riskSignals()).isNotNull();

        // Retrieve created review by ID
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/commit-reviews/" + created.id()))
                .GET()
                .build();

        HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertThat(getResp.statusCode()).isEqualTo(200);

        CommitReviewResponse retrieved = objectMapper.readValue(getResp.body(), CommitReviewResponse.class);
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.fromCommit()).isEqualTo(created.fromCommit());
        assertThat(retrieved.toCommit()).isEqualTo(created.toCommit());
    }

    @Test
    @DisplayName("GET /api/repositories/{id}/commits links existing reviewId")
    void listCommitsIncludesReviewIdWhenReviewExists() throws Exception {
        // Create an existing review in database
        CommitReviewEntity review = commitReviews.save(new CommitReviewEntity(
                repoId,
                "commit-hash-1111111111111111111111111111111111111111",
                "commit-hash-2222222222222222222222222222222222222222",
                1, 0, 0, 0, 0,
                "{}", "[]", "AI summary", "[]", "[]", "gemini", 1L, Instant.now()));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/repositories/" + repoId + "/commits"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);

        CommitListResponse body = objectMapper.readValue(resp.body(), CommitListResponse.class);
        assertThat(body.commits().get(0).reviewId()).isEqualTo(review.getId());
    }
}
