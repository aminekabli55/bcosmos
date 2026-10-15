package com.bcosmos.backend.ai.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.web.dto.BenchmarkResponses.BenchmarkRunResponse;
import com.bcosmos.backend.web.dto.BenchmarkResponses.CreateBenchmarkRunRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Benchmark leaderboard & evaluation runs API (Phase 6, spec section 7.4 & 8)")
class BenchmarkIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("GET /api/benchmarks returns seeded baseline conditions ranked by targetResolvedRate")
    void getLeaderboardReturnsRankedConditions() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/benchmarks"))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        var runs = json.readValue(response.body(), new TypeReference<java.util.List<BenchmarkRunResponse>>() {});
        assertThat(runs).hasSizeGreaterThanOrEqualTo(3);

        // Leaderboard must be sorted descending by targetResolvedRate
        for (int i = 0; i < runs.size() - 1; i++) {
            assertThat(runs.get(i).targetResolvedRate())
                    .isGreaterThanOrEqualTo(runs.get(i + 1).targetResolvedRate());
        }

        // Verify the 3 distinct conditions are present
        var conditionNames = runs.stream().map(BenchmarkRunResponse::conditionName).toList();
        assertThat(conditionNames).contains(
                "Gemini 2.5 Flash (RAG)",
                "OpenAI GPT-4o-mini (RAG)",
                "Gemini 2.5 Flash (No Retrieval)"
        );

        // Verify primary metric: RAG clearly beats No-Retrieval
        var ragRun = runs.stream().filter(r -> r.conditionName().equals("Gemini 2.5 Flash (RAG)")).findFirst().orElseThrow();
        var noRetrievalRun = runs.stream().filter(r -> r.conditionName().equals("Gemini 2.5 Flash (No Retrieval)")).findFirst().orElseThrow();
        assertThat(ragRun.targetResolvedRate()).isGreaterThan(noRetrievalRun.targetResolvedRate());
    }

    @Test
    @DisplayName("POST /api/benchmarks records a new evaluation benchmark run")
    void postBenchmarkRunStoresNewResult() throws Exception {
        CreateBenchmarkRunRequest newRun = new CreateBenchmarkRunRequest(
                "eval-test-new",
                "v20261014-custom",
                "Claude 3.5 Sonnet (RAG)",
                "claude-3-5-sonnet",
                "suggest-fix.v1",
                "RAG",
                50,
                0.98,
                0.96,
                0.92,
                0.78,
                0.84,
                7,
                1650.0,
                82000L,
                0.125,
                "{\"rulesEvaluated\":[\"EmptyCatchBlock\"]}",
                "Anthropic Sonnet evaluation test"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/benchmarks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(newRun)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);

        BenchmarkRunResponse created = json.readValue(response.body(), BenchmarkRunResponse.class);
        assertThat(created.id()).isPositive();
        assertThat(created.conditionName()).isEqualTo("Claude 3.5 Sonnet (RAG)");
        assertThat(created.targetResolvedRate()).isEqualTo(0.78);
        assertThat(created.notes()).isEqualTo("Anthropic Sonnet evaluation test");
    }
}
