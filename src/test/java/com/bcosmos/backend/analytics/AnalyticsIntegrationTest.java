package com.bcosmos.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase 5 acceptance for the analytics API (spec v2 section 8, {@code GET /api/analytics/*}).
 *
 * <p>The gold rows are inserted with plain SQL, and the endpoints are queried over HTTP: the schema comes from the
 * real Liquibase changelog and the answers come from the real controller, so a column renamed in changeset 0018
 * fails here instead of on the screen.</p>
 *
 * <p>The fixture holds two snapshots on purpose. The older one must be ignored everywhere: a snapshot load replaces
 * the tables, and an endpoint that forgot its {@code dataset_version} filter would silently mix them.</p>
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("GET /api/analytics/* (Phase 5, spec v2 section 6)")
class AnalyticsIntegrationTest {

    private static final String VERSION = "v20260504-abc12345";
    private static final String OLDER = "v20260401-00000000";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM agg_finding");
        jdbc.update("DELETE FROM agg_dataset_repository");
        jdbc.update("DELETE FROM agg_dataset");
        jdbc.update("DELETE FROM agg_hotspot");
        jdbc.update("DELETE FROM agg_time_to_fix");
        jdbc.update("DELETE FROM agg_quality_trend");
        jdbc.update("DELETE FROM agg_rule_distribution");
        jdbc.update("DELETE FROM agg_pipeline_stat");
        jdbc.update("DELETE FROM gold_run");
        jdbc.update("DELETE FROM repository");

        jdbc.update("INSERT INTO repository (id, name, canonical_local_path, source_type, primary_language, status, "
                + "created_at, license_spdx, remote_url) VALUES (1, 'demo', NULL, 'MINED', 'java', 'REGISTERED', "
                + "now(), 'Apache-2.0', 'https://github.com/example/demo')");
        jdbc.update("INSERT INTO repository (id, name, canonical_local_path, source_type, primary_language, status, "
                + "created_at, license_spdx, remote_url) VALUES (2, 'other', NULL, 'MINED', 'java', 'REGISTERED', "
                + "now(), NULL, 'https://github.com/example/other')");

        jdbc.update("INSERT INTO gold_run (dataset_version, generated_at, source, repository_count, triple_count, "
                + "notes) VALUES (?, now() - interval '1 day', 'spark', 1, 1, 'older run')", OLDER);
        jdbc.update("INSERT INTO gold_run (dataset_version, generated_at, source, repository_count, triple_count, "
                + "notes) VALUES (?, now(), 'spark', 2, 7, 'current run')", VERSION);

        // The older snapshot has a rule that the current one does not, and a bigger count for a shared rule: if any
        // endpoint lost its version filter, both numbers would show up.
        insertRule(OLDER, 1, "EmptyCatchBlock", "MEDIUM", 99, 5, 100, 9.9, 0);
        insertRule(VERSION, 1, "EmptyCatchBlock", "MEDIUM", 3, 2, 400, 7.5, 1);
        insertRule(VERSION, 1, "UnusedPrivateField", "LOW", 1, 1, 400, 2.5, 0);
        insertRule(VERSION, 2, "EmptyCatchBlock", "HIGH", 4, 3, 200, 20.0, 0);

        jdbc.update("INSERT INTO agg_quality_trend (dataset_version, generated_at, repository_id, bucket_start, "
                + "findings, files_analysed, measured_lines, density_per_kloc, parse_failures) VALUES "
                + "(?, now(), 1, DATE '2026-05-04', 2, 1, 100, 20.0, 0)", VERSION);
        jdbc.update("INSERT INTO agg_quality_trend (dataset_version, generated_at, repository_id, bucket_start, "
                + "findings, files_analysed, measured_lines, density_per_kloc, parse_failures) VALUES "
                + "(?, now(), 1, DATE '2026-05-11', 1, 1, 300, 3.3333, 1)", VERSION);

        jdbc.update("INSERT INTO agg_time_to_fix (dataset_version, generated_at, repository_id, rule_id, "
                + "commits_until_resolved, resolved_pairs, observed_introductions) VALUES (?, now(), 1, "
                + "'EmptyCatchBlock', 1, 2, 2)", VERSION);
        jdbc.update("INSERT INTO agg_time_to_fix (dataset_version, generated_at, repository_id, rule_id, "
                + "commits_until_resolved, resolved_pairs, observed_introductions) VALUES (?, now(), 1, "
                + "'EmptyCatchBlock', NULL, 1, 0)", VERSION);

        jdbc.update("INSERT INTO agg_hotspot (dataset_version, generated_at, repository_id, file_path, score, "
                + "rank_in_repository, churn, churn_score, churn_contribution, complexity_proxy, complexity_score, "
                + "complexity_contribution, open_findings, open_findings_score, open_findings_contribution, "
                + "dependency_measured, weight_sum_measured, latest_commit_hash, weights) VALUES "
                + "(?, now(), 1, 'src/main/java/com/example/Sample.java', 0.5, 1, 3, 1.0, 0.35, 3, 1.0, 0.25, "
                + "3, 1.0, 0.40, false, 1.0, 'c0ffee', 'churn=0.35,complexity=0.25,dependency=0.0,"
                + "open_findings=0.4')", VERSION);

        insertFinding(VERSION, 1, "c0ffee", "src/main/java/com/example/Sample.java", "AFTER", "EmptyCatchBlock",
                "MEDIUM", "com.example.Sample.test", "fp-1", 10, 12, "2026-05-04T10:00:00Z");
        insertFinding(VERSION, 1, "c0ffee", "src/main/java/com/example/Sample.java", "AFTER", "UnusedPrivateField",
                "LOW", "com.example.Sample.other", "fp-2", 20, 20, "2026-05-04T10:00:00Z");
        insertFinding(VERSION, 2, "d1e2f3", "src/main/java/com/example/Other.java", "AFTER", "EmptyCatchBlock",
                "HIGH", "com.example.Other.run", "fp-3", 5, 6, "2026-05-11T09:00:00Z");

        jdbc.update("INSERT INTO agg_dataset (dataset_version, generated_at, split, triples, repositories, rules, "
                + "snippet_changed, introductions_unobserved, max_changed_lines, filters) VALUES "
                + "(?, now(), 'train', 5, 1, 2, 4, 1, 30, 'max_changed_lines filter')", VERSION);
        jdbc.update("INSERT INTO agg_dataset (dataset_version, generated_at, split, triples, repositories, rules, "
                + "snippet_changed, introductions_unobserved, max_changed_lines, filters) VALUES "
                + "(?, now(), 'test', 2, 1, 1, 2, 0, 30, 'max_changed_lines filter')", VERSION);
        jdbc.update("INSERT INTO agg_dataset_repository (dataset_version, generated_at, split, repository_id, "
                + "repository_name, license_spdx, triples) VALUES (?, now(), 'train', 1, 'demo', 'Apache-2.0', 5)",
                VERSION);
        jdbc.update("INSERT INTO agg_dataset_repository (dataset_version, generated_at, split, repository_id, "
                + "repository_name, license_spdx, triples) VALUES (?, now(), 'test', 2, 'other', NULL, 2)", VERSION);

        for (Map.Entry<String, Long> stat : Map.of("silver_rows", 120L, "rejected_rows", 4L, "drilldown_commits", 50L)
                .entrySet()) {
            jdbc.update("INSERT INTO agg_pipeline_stat (dataset_version, generated_at, stat_key, stat_value) "
                    + "VALUES (?, now(), ?, ?)", VERSION, stat.getKey(), stat.getValue());
        }
    }

    @Test
    @DisplayName("the overview reports the newest snapshot only, with its version and timestamp")
    void overviewReportsTheNewestSnapshot() throws Exception {
        JsonNode overview = get("/api/analytics/overview");

        assertThat(overview.path("available").asBoolean()).isTrue();
        assertThat(overview.path("datasetVersion").asString()).isEqualTo(VERSION);
        assertThat(overview.path("source").asString()).isEqualTo("spark");
        assertThat(overview.path("generatedAt").asString()).isNotBlank();
        assertThat(overview.path("drilldownCommits").asInt()).isEqualTo(50);
        assertThat(overview.path("repositories").asLong()).isEqualTo(2);
        assertThat(overview.path("triples").asLong()).isEqualTo(7);

        // 3 + 1 + 4 findings; the older snapshot's 99 must not appear.
        assertThat(overview.path("findings").asLong()).isEqualTo(8);
        assertThat(overview.path("rules").asLong()).isEqualTo(2);

        // Measured lines and parse failures are per repository: counting the rows would repeat them per rule.
        assertThat(overview.path("measuredLines").asLong()).isEqualTo(600);
        assertThat(overview.path("parseFailures").asLong()).isEqualTo(1);

        JsonNode severityMix = overview.path("severityMix");
        assertThat(severityMix).hasSize(3);
        assertThat(severityMix.get(0).path("severity").asString()).isEqualTo("HIGH");
        assertThat(severityMix.get(0).path("findings").asLong()).isEqualTo(4);
        assertThat(severityMix.get(2).path("severity").asString()).isEqualTo("LOW");

        // The count is `repositories`; the list is `repositoryRefs`, so a chart cannot mistake one for the other.
        assertThat(overview.path("repositories").isNumber()).isTrue();
        assertThat(overview.path("repositoryRefs").isArray()).isTrue();
        assertThat(overview.path("repositoryRefs").get(0).path("name").asString()).isEqualTo("demo");
        assertThat(overview.path("repositoryRefs").get(0).path("licenseSpdx").asString()).isEqualTo("Apache-2.0");
    }

    @Test
    @DisplayName("rules are ranked inside their repository and paginated")
    void rulesAreRankedAndPaginated() throws Exception {
        JsonNode firstPage = get("/api/analytics/rules?size=2");

        assertThat(firstPage.path("totalItems").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("hasNext").asBoolean()).isTrue();
        assertThat(firstPage.path("items")).hasSize(2);

        JsonNode top = firstPage.path("items").get(0);
        assertThat(top.path("repositoryId").asLong()).isEqualTo(1);
        assertThat(top.path("ruleId").asString()).isEqualTo("EmptyCatchBlock");
        assertThat(top.path("findingsPerKloc").asDouble()).isEqualTo(7.5);
        assertThat(top.path("rank").asInt()).isEqualTo(1);

        JsonNode secondPage = get("/api/analytics/rules?size=2&page=1");
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a rule with no measured lines reports a null density, never zero")
    void aRuleWithoutMeasuredLinesHasNoDensity() throws Exception {
        jdbc.update("INSERT INTO agg_rule_distribution (dataset_version, generated_at, repository_id, rule_id, "
                + "severity, findings, files_affected, measured_lines, findings_per_kloc, parse_failures) VALUES "
                + "(?, now(), 2, 'NoMeasurement', 'LOW', 2, 1, 0, NULL, 0)", VERSION);

        JsonNode items = get("/api/analytics/rules?repositoryId=2").path("items");
        JsonNode row = items.get(items.size() - 1);
        assertThat(row.path("ruleId").asString()).isEqualTo("NoMeasurement");
        assertThat(row.path("findingsPerKloc").isNull()).isTrue();
    }

    @Test
    @DisplayName("filters narrow the result set")
    void filtersNarrowTheResults() throws Exception {
        assertThat(get("/api/analytics/rules?repositoryId=2").path("totalItems").asLong()).isEqualTo(1);
        assertThat(get("/api/analytics/rules?severity=LOW").path("totalItems").asLong()).isEqualTo(1);
        assertThat(get("/api/analytics/trends?repositoryId=1").path("items")).hasSize(2);
        assertThat(get("/api/analytics/trends?repositoryId=2").path("items")).isEmpty();
    }

    @Test
    @DisplayName("the trend buckets are ordered and carry their density")
    void trendBucketsAreOrdered() throws Exception {
        JsonNode items = get("/api/analytics/trends").path("items");

        assertThat(items.get(0).path("bucketStart").asString()).isEqualTo("2026-05-04");
        assertThat(items.get(0).path("densityPerKloc").asDouble()).isEqualTo(20.0);
        assertThat(items.get(1).path("bucketStart").asString()).isEqualTo("2026-05-11");
        assertThat(items.get(1).path("parseFailures").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("the time-to-fix histogram keeps the null distance row apart")
    void timeToFixKeepsTheLowerBoundRow() throws Exception {
        JsonNode items = get("/api/analytics/time-to-fix").path("items");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).path("commitsUntilResolved").asInt()).isEqualTo(1);
        assertThat(items.get(0).path("resolvedPairs").asLong()).isEqualTo(2);
        assertThat(items.get(0).path("observedIntroductions").asLong()).isEqualTo(2);
        assertThat(items.get(1).path("commitsUntilResolved").isNull()).isTrue();
        assertThat(items.get(1).path("observedIntroductions").asLong()).isZero();
    }

    @Test
    @DisplayName("a hotspot carries every component of its score")
    void hotspotsCarryTheirComponents() throws Exception {
        JsonNode hotspot = get("/api/analytics/hotspots").path("items").get(0);

        assertThat(hotspot.path("filePath").asString()).isEqualTo("src/main/java/com/example/Sample.java");
        assertThat(hotspot.path("rankInRepository").asInt()).isEqualTo(1);
        assertThat(hotspot.path("score").asDouble()).isEqualTo(0.5);
        assertThat(hotspot.path("churn").asLong()).isEqualTo(3);
        assertThat(hotspot.path("churnContribution").asDouble()).isEqualTo(0.35);
        assertThat(hotspot.path("complexityContribution").asDouble()).isEqualTo(0.25);
        assertThat(hotspot.path("openFindingsContribution").asDouble()).isEqualTo(0.40);
        assertThat(hotspot.path("dependencyMeasured").asBoolean()).isFalse();
        assertThat(hotspot.path("weightSumMeasured").asDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the dataset splits and their repositories are reported with licences")
    void datasetSplitsAndRepositories() throws Exception {
        JsonNode splits = get("/api/analytics/dataset/splits").path("items");
        assertThat(splits).hasSize(2);
        assertThat(splits.get(0).path("split").asString()).isEqualTo("test");
        assertThat(splits.get(1).path("split").asString()).isEqualTo("train");
        assertThat(splits.get(1).path("snippetChanged").asLong()).isEqualTo(4);

        JsonNode repositories = get("/api/analytics/dataset/repositories").path("items");
        assertThat(repositories).hasSize(2);
        // Ordered by triples: the train repository first, and the missing licence is null, not an empty string.
        assertThat(repositories.get(0).path("name").asString()).isEqualTo("demo");
        assertThat(repositories.get(0).path("licenseSpdx").asString()).isEqualTo("Apache-2.0");
        assertThat(repositories.get(1).path("licenseSpdx").isNull()).isTrue();

        assertThat(get("/api/analytics/dataset/repositories?split=test").path("items")).hasSize(1);
    }

    @Test
    @DisplayName("the drill-down returns the records behind an aggregate")
    void drillDownReturnsTheRecords() throws Exception {
        JsonNode byRule = get("/api/analytics/findings?ruleId=EmptyCatchBlock");
        assertThat(byRule.path("totalItems").asLong()).isEqualTo(2);
        JsonNode first = byRule.path("items").get(0);
        assertThat(first.path("ruleId").asString()).isEqualTo("EmptyCatchBlock");
        assertThat(first.path("snippet")).isNotNull();
        assertThat(first.path("startLine").asInt()).isGreaterThan(0);

        // The dimensions of the other charts: a hotspot row and a trend bucket.
        assertThat(get("/api/analytics/findings?filePath=src/main/java/com/example/Sample.java")
                .path("totalItems").asLong()).isEqualTo(2);
        assertThat(get("/api/analytics/findings?repositoryId=1&from=2026-05-04&to=2026-05-04")
                .path("totalItems").asLong()).isEqualTo(2);
        assertThat(get("/api/analytics/findings?severity=HIGH").path("totalItems").asLong()).isEqualTo(1);
        assertThat(get("/api/analytics/findings?repositoryId=2").path("totalItems").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("with no gold load at all the API answers \"nothing yet\" instead of failing")
    void withoutAGoldLoadTheApiIsEmpty() throws Exception {
        jdbc.update("DELETE FROM agg_finding");
        jdbc.update("DELETE FROM agg_dataset_repository");
        jdbc.update("DELETE FROM agg_dataset");
        jdbc.update("DELETE FROM agg_hotspot");
        jdbc.update("DELETE FROM agg_time_to_fix");
        jdbc.update("DELETE FROM agg_quality_trend");
        jdbc.update("DELETE FROM agg_rule_distribution");
        jdbc.update("DELETE FROM agg_pipeline_stat");
        jdbc.update("DELETE FROM gold_run");

        JsonNode overview = get("/api/analytics/overview");
        assertThat(overview.path("available").asBoolean()).isFalse();
        assertThat(overview.path("datasetVersion").isNull()).isTrue();
        assertThat(overview.path("topRules")).isEmpty();

        assertThat(get("/api/analytics/rules").path("items")).isEmpty();
        assertThat(get("/api/analytics/hotspots").path("totalItems").asLong()).isZero();
        assertThat(get("/api/analytics/dataset/splits").path("items")).isEmpty();
        assertThat(get("/api/analytics/findings").path("items")).isEmpty();
    }

    @Test
    @DisplayName("an oversized page request is clamped instead of asking the database for everything")
    void pageSizeIsClamped() throws Exception {
        JsonNode response = get("/api/analytics/rules?size=100000");
        assertThat(response.path("size").asInt()).isEqualTo(com.bcosmos.backend.web.Pagination.MAX_SIZE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void insertRule(String version, long repositoryId, String ruleId, String severity, long findings,
            long filesAffected, long measuredLines, double perKloc, long parseFailures) {
        jdbc.update("INSERT INTO agg_rule_distribution (dataset_version, generated_at, repository_id, rule_id, "
                + "severity, findings, files_affected, measured_lines, findings_per_kloc, parse_failures) VALUES "
                + "(?, now(), ?, ?, ?, ?, ?, ?, ?, ?)", version, repositoryId, ruleId, severity, findings,
                filesAffected, measuredLines, perKloc, parseFailures);
    }

    private void insertFinding(String version, long repositoryId, String commitHash, String filePath, String side,
            String ruleId, String severity, String symbol, String fingerprint, int startLine, int endLine,
            String occurredAt) {
        jdbc.update("INSERT INTO agg_finding (dataset_version, generated_at, repository_id, commit_hash, file_path, "
                + "side, rule_id, severity, symbol, fingerprint, start_line, end_line, line_count, occurred_at, "
                + "snippet) VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 100, ?::timestamptz, 'snippet')",
                version, repositoryId, commitHash, filePath, side, ruleId, severity, symbol, fingerprint, startLine,
                endLine, occurredAt);
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("GET %s -> %s", path, response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
