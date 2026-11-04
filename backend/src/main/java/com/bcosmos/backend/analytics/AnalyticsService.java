package com.bcosmos.backend.analytics;

import com.bcosmos.backend.web.dto.AnalyticsResponses.DatasetRepository;
import com.bcosmos.backend.web.dto.AnalyticsResponses.DatasetSplit;
import com.bcosmos.backend.web.dto.AnalyticsResponses.DrilldownFinding;
import com.bcosmos.backend.web.dto.AnalyticsResponses.Hotspot;
import com.bcosmos.backend.web.dto.AnalyticsResponses.Overview;
import com.bcosmos.backend.web.dto.AnalyticsResponses.PipelineStat;
import com.bcosmos.backend.web.dto.AnalyticsResponses.RepositoryRef;
import com.bcosmos.backend.web.dto.AnalyticsResponses.RuleStat;
import com.bcosmos.backend.web.dto.AnalyticsResponses.SeverityCount;
import com.bcosmos.backend.web.dto.AnalyticsResponses.TimeToFixBucket;
import com.bcosmos.backend.web.dto.AnalyticsResponses.TrendPoint;
import com.bcosmos.backend.web.dto.PagedResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the Spark gold tables (spec v2 section 6.1, {@code GET /api/analytics/*}).
 *
 * <p>Plain SQL on purpose. These are read-only aggregate tables whose shape is owned by Liquibase changeset 0018 and
 * written by a Python job, so an entity per table would add a second, silently divergent definition of the same
 * schema and nothing but a test would notice a mismatch. The SQL names its columns, and the integration test runs
 * every query against the real changelog on a container, which is the check that matters.</p>
 *
 * <p>Every query filters on the newest {@code gold_run}: the tables hold one snapshot, and reading without a version
 * would keep working after a later load and quietly mix two runs.</p>
 */
@Service
public class AnalyticsService {

    /** The number of rule rows the overview carries; the full list is the paged endpoint's job. */
    private static final int OVERVIEW_RULE_LIMIT = 8;

    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The snapshot every other answer is relative to. */
    public record Snapshot(
            String datasetVersion,
            Instant generatedAt,
            String source,
            long tripleCount,
            int repositoryCount) {
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> currentSnapshot() {
        List<Snapshot> snapshots = jdbc.query(
                "SELECT dataset_version, generated_at, source, triple_count, repository_count "
                        + "FROM gold_run ORDER BY generated_at DESC, id DESC LIMIT 1",
                (rs, rowNum) -> new Snapshot(rs.getString("dataset_version"),
                        rs.getTimestamp("generated_at").toInstant(), rs.getString("source"),
                        rs.getLong("triple_count"), rs.getInt("repository_count")));
        return snapshots.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return Overview.unavailable();
        }
        Snapshot current = snapshot.get();
        String version = current.datasetVersion();

        return new Overview(
                true,
                version,
                current.generatedAt(),
                current.source(),
                drilldownCommits(version),
                current.repositoryCount(),
                scalar("SELECT COUNT(DISTINCT rule_id) FROM agg_rule_distribution WHERE dataset_version = ?", version),
                scalar("SELECT COALESCE(SUM(findings), 0) FROM agg_rule_distribution WHERE dataset_version = ?",
                        version),
                // Lines and parse failures live per repository in a table keyed by rule: summing the rows directly
                // would count the same measured line once per rule.
                scalar("SELECT COALESCE(SUM(measured_lines), 0) FROM (SELECT DISTINCT repository_id, measured_lines "
                        + "FROM agg_rule_distribution WHERE dataset_version = ?) per_repository", version),
                scalar("SELECT COALESCE(SUM(parse_failures), 0) FROM (SELECT DISTINCT repository_id, parse_failures "
                        + "FROM agg_rule_distribution WHERE dataset_version = ?) per_repository", version),
                current.tripleCount(),
                severityMix(version),
                topRules(version),
                pipelineStats(version),
                repositoriesWithData(version),
                splits(version));
    }

    @Transactional(readOnly = true)
    public PagedResponse<RuleStat> rules(Long repositoryId, String severity, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        String filters = filters(parameters, repositoryId, severity, "severity");

        String ranked = "SELECT repository_id, rule_id, severity, findings, files_affected, measured_lines, "
                + "findings_per_kloc, parse_failures, "
                + "ROW_NUMBER() OVER (PARTITION BY repository_id ORDER BY findings DESC, rule_id ASC, severity ASC) "
                + "AS rank_in_repository FROM agg_rule_distribution WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) FROM (" + ranked + ") ranked", parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT * FROM (" + ranked + ") ranked "
                + "ORDER BY rank_in_repository ASC, repository_id ASC LIMIT ? OFFSET ?", RULE_STAT, pageParameters.toArray()),
                page, size, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TrendPoint> trends(Long repositoryId, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        String filters = filters(parameters, repositoryId, null, null);
        String sql = "FROM agg_quality_trend WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) " + sql, parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT repository_id, bucket_start, findings, files_analysed, measured_lines, "
                + "density_per_kloc, parse_failures " + sql
                + " ORDER BY bucket_start ASC, repository_id ASC LIMIT ? OFFSET ?", TREND_POINT, pageParameters.toArray()),
                page, size, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TimeToFixBucket> timeToFix(Long repositoryId, String ruleId, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        String filters = filters(parameters, repositoryId, null, null);
        if (ruleId != null && !ruleId.isBlank()) {
            filters += " AND rule_id = ?";
            parameters.add(ruleId);
        }
        String sql = "FROM agg_time_to_fix WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) " + sql, parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT repository_id, rule_id, commits_until_resolved, resolved_pairs, "
                + "observed_introductions " + sql
                + " ORDER BY rule_id ASC, commits_until_resolved ASC NULLS LAST LIMIT ? OFFSET ?", TIME_TO_FIX,
                pageParameters.toArray()), page, size, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<Hotspot> hotspots(Long repositoryId, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        String filters = filters(parameters, repositoryId, null, null);
        String sql = "FROM agg_hotspot WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) " + sql, parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT repository_id, file_path, score, rank_in_repository, churn, churn_score, "
                + "churn_contribution, complexity_proxy, complexity_score, complexity_contribution, open_findings, "
                + "open_findings_score, open_findings_contribution, dependency_measured, weight_sum_measured, "
                + "latest_commit_hash, weights " + sql
                + " ORDER BY repository_id ASC, rank_in_repository ASC LIMIT ? OFFSET ?", HOTSPOT,
                pageParameters.toArray()), page, size, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DatasetSplit> splits(int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        Object[] parameters = {snapshot.get().datasetVersion()};
        long total = count("SELECT COUNT(*) FROM agg_dataset WHERE dataset_version = ?", List.of(parameters));
        List<Object> pageParameters = new ArrayList<>(List.of(parameters));
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT split, triples, repositories, rules, snippet_changed, "
                + "introductions_unobserved, max_changed_lines FROM agg_dataset WHERE dataset_version = ? "
                + "ORDER BY split ASC LIMIT ? OFFSET ?", DATASET_SPLIT, pageParameters.toArray()), page, size, total);
    }

    @Transactional(readOnly = true)
    public PagedResponse<DatasetRepository> datasetRepositories(String split, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        String filters = "";
        if (split != null && !split.isBlank()) {
            filters = " AND split = ?";
            parameters.add(split);
        }
        String sql = "FROM agg_dataset_repository WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) " + sql, parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT split, repository_id, repository_name, license_spdx, triples " + sql
                + " ORDER BY triples DESC, repository_id ASC LIMIT ? OFFSET ?", DATASET_REPOSITORY,
                pageParameters.toArray()), page, size, total);
    }

    /**
     * The drill-down behind every chart: the findings an aggregate was computed from.
     *
     * <p>Each chart passes the dimension it grouped by, so clicking a bar, a point or a row lands on the records that
     * produced it. The rows are limited by the loader (the most recent commits per repository), which the overview
     * reports as {@code drilldownCommits}.</p>
     */
    @Transactional(readOnly = true)
    public PagedResponse<DrilldownFinding> findings(Long repositoryId, String ruleId, String filePath, String severity,
            LocalDate from, LocalDate to, int page, int size) {
        Optional<Snapshot> snapshot = currentSnapshot();
        if (snapshot.isEmpty()) {
            return empty(page, size);
        }
        List<Object> parameters = new ArrayList<>(List.of(snapshot.get().datasetVersion()));
        StringBuilder filters = new StringBuilder();
        if (repositoryId != null) {
            filters.append(" AND repository_id = ?");
            parameters.add(repositoryId);
        }
        if (ruleId != null && !ruleId.isBlank()) {
            filters.append(" AND rule_id = ?");
            parameters.add(ruleId);
        }
        if (filePath != null && !filePath.isBlank()) {
            filters.append(" AND file_path = ?");
            parameters.add(filePath);
        }
        if (severity != null && !severity.isBlank()) {
            filters.append(" AND severity = ?");
            parameters.add(severity);
        }
        if (from != null) {
            filters.append(" AND occurred_at >= ?");
            parameters.add(java.sql.Timestamp.from(from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        }
        if (to != null) {
            // Inclusive day: `to` means "up to the end of that day", which is what a week bucket click implies.
            filters.append(" AND occurred_at < ?");
            parameters.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        }
        String sql = "FROM agg_finding WHERE dataset_version = ?" + filters;

        long total = count("SELECT COUNT(*) " + sql, parameters);
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);

        return paged(jdbc.query("SELECT id, repository_id, commit_hash, file_path, side, rule_id, severity, symbol, "
                + "fingerprint, start_line, end_line, line_count, occurred_at, snippet " + sql
                + " ORDER BY occurred_at DESC, rule_id ASC, file_path ASC, start_line ASC LIMIT ? OFFSET ?",
                DRILLDOWN_FINDING, pageParameters.toArray()), page, size, total);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Integer drilldownCommits(String datasetVersion) {
        List<Integer> values = jdbc.query(
                "SELECT stat_value FROM agg_pipeline_stat WHERE dataset_version = ? AND stat_key = 'drilldown_commits'",
                (rs, rowNum) -> rs.getInt("stat_value"), datasetVersion);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<SeverityCount> severityMix(String datasetVersion) {
        return jdbc.query("SELECT severity, SUM(findings) AS findings FROM agg_rule_distribution "
                + "WHERE dataset_version = ? AND severity IS NOT NULL GROUP BY severity "
                + "ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 "
                + "WHEN 'LOW' THEN 3 ELSE 4 END", (rs, rowNum) -> new SeverityCount(rs.getString("severity"),
                rs.getLong("findings")), datasetVersion);
    }

    private List<RuleStat> topRules(String datasetVersion) {
        return jdbc.query("SELECT * FROM (SELECT repository_id, rule_id, severity, findings, files_affected, "
                + "measured_lines, findings_per_kloc, parse_failures, "
                + "ROW_NUMBER() OVER (PARTITION BY repository_id ORDER BY findings DESC, rule_id ASC, severity ASC) "
                + "AS rank_in_repository FROM agg_rule_distribution WHERE dataset_version = ?) ranked "
                + "ORDER BY findings DESC, rule_id ASC LIMIT " + OVERVIEW_RULE_LIMIT, RULE_STAT, datasetVersion);
    }

    private List<PipelineStat> pipelineStats(String datasetVersion) {
        return jdbc.query("SELECT stat_key, stat_value FROM agg_pipeline_stat WHERE dataset_version = ? "
                + "ORDER BY stat_key ASC", (rs, rowNum) -> new PipelineStat(rs.getString("stat_key"),
                rs.getLong("stat_value")), datasetVersion);
    }

    private List<RepositoryRef> repositoriesWithData(String datasetVersion) {
        return jdbc.query("SELECT r.id, r.name, r.source_type, r.license_spdx FROM repository r "
                + "WHERE EXISTS (SELECT 1 FROM agg_rule_distribution a WHERE a.repository_id = r.id "
                + "AND a.dataset_version = ?) ORDER BY r.name ASC", (rs, rowNum) -> new RepositoryRef(
                rs.getLong("id"), rs.getString("name"), rs.getString("source_type"), rs.getString("license_spdx")),
                datasetVersion);
    }

    private List<DatasetSplit> splits(String datasetVersion) {
        return jdbc.query("SELECT split, triples, repositories, rules, snippet_changed, introductions_unobserved, "
                + "max_changed_lines FROM agg_dataset WHERE dataset_version = ? ORDER BY split ASC", DATASET_SPLIT,
                datasetVersion);
    }

    /**
     * Appends the filters the aggregate endpoints share and returns their SQL fragment.
     *
     * <p>`parameters` is passed in and appended to, so the `?` order in the fragment and the parameter list cannot
     * drift apart: they are built in the same place, in the same order.</p>
     */
    private String filters(List<Object> parameters, Long repositoryId, String severity, String severityColumn) {
        StringBuilder filters = new StringBuilder();
        if (repositoryId != null) {
            filters.append(" AND repository_id = ?");
            parameters.add(repositoryId);
        }
        if (severity != null && !severity.isBlank() && severityColumn != null) {
            filters.append(" AND ").append(severityColumn).append(" = ?");
            parameters.add(severity);
        }
        return filters.toString();
    }

    private long count(String sql, List<Object> parameters) {
        Long value = jdbc.queryForObject(sql, Long.class, parameters.toArray());
        return value == null ? 0 : value;
    }

    private long scalar(String sql, Object... parameters) {
        Long value = jdbc.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private static <T> PagedResponse<T> paged(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PagedResponse<>(items, page, size, total, totalPages, (long) (page + 1) * size < total);
    }

    private static <T> PagedResponse<T> empty(int page, int size) {
        return new PagedResponse<>(List.of(), page, size, 0, 0, false);
    }

    // ── row mappers ──────────────────────────────────────────────────────────

    private static final RowMapper<RuleStat> RULE_STAT = (rs, rowNum) -> new RuleStat(
            rs.getLong("repository_id"),
            rs.getString("rule_id"),
            rs.getString("severity"),
            rs.getLong("findings"),
            rs.getLong("files_affected"),
            rs.getLong("measured_lines"),
            nullableDouble(rs, "findings_per_kloc"),
            rs.getLong("parse_failures"),
            rs.getInt("rank_in_repository"));

    private static final RowMapper<TrendPoint> TREND_POINT = (rs, rowNum) -> new TrendPoint(
            rs.getLong("repository_id"),
            rs.getObject("bucket_start", LocalDate.class),
            rs.getLong("findings"),
            rs.getLong("files_analysed"),
            rs.getLong("measured_lines"),
            nullableDouble(rs, "density_per_kloc"),
            rs.getLong("parse_failures"));

    private static final RowMapper<TimeToFixBucket> TIME_TO_FIX = (rs, rowNum) -> new TimeToFixBucket(
            rs.getLong("repository_id"),
            rs.getString("rule_id"),
            nullableInt(rs, "commits_until_resolved"),
            rs.getLong("resolved_pairs"),
            rs.getLong("observed_introductions"));

    private static final RowMapper<Hotspot> HOTSPOT = (rs, rowNum) -> new Hotspot(
            rs.getLong("repository_id"),
            rs.getString("file_path"),
            rs.getDouble("score"),
            rs.getInt("rank_in_repository"),
            rs.getLong("churn"),
            nullableDouble(rs, "churn_score"),
            nullableDouble(rs, "churn_contribution"),
            rs.getLong("complexity_proxy"),
            nullableDouble(rs, "complexity_score"),
            nullableDouble(rs, "complexity_contribution"),
            rs.getLong("open_findings"),
            nullableDouble(rs, "open_findings_score"),
            nullableDouble(rs, "open_findings_contribution"),
            rs.getBoolean("dependency_measured"),
            rs.getDouble("weight_sum_measured"),
            rs.getString("latest_commit_hash"),
            rs.getString("weights"));

    private static final RowMapper<DatasetSplit> DATASET_SPLIT = (rs, rowNum) -> new DatasetSplit(
            rs.getString("split"),
            rs.getLong("triples"),
            rs.getInt("repositories"),
            rs.getInt("rules"),
            rs.getLong("snippet_changed"),
            rs.getLong("introductions_unobserved"),
            rs.getInt("max_changed_lines"));

    private static final RowMapper<DatasetRepository> DATASET_REPOSITORY = (rs, rowNum) -> new DatasetRepository(
            rs.getString("split"),
            rs.getLong("repository_id"),
            rs.getString("repository_name"),
            rs.getString("license_spdx"),
            rs.getLong("triples"));

    private static final RowMapper<DrilldownFinding> DRILLDOWN_FINDING = (rs, rowNum) -> new DrilldownFinding(
            rs.getLong("id"),
            rs.getLong("repository_id"),
            rs.getString("commit_hash"),
            rs.getString("file_path"),
            rs.getString("side"),
            rs.getString("rule_id"),
            rs.getString("severity"),
            rs.getString("symbol"),
            rs.getString("fingerprint"),
            rs.getInt("start_line"),
            rs.getInt("end_line"),
            nullableInt(rs, "line_count"),
            rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("snippet"));

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
