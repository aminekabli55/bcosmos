package com.bcosmos.backend.web;

import com.bcosmos.backend.analytics.AnalyticsService;
import com.bcosmos.backend.web.dto.AnalyticsResponses.DatasetRepository;
import com.bcosmos.backend.web.dto.AnalyticsResponses.DatasetSplit;
import com.bcosmos.backend.web.dto.AnalyticsResponses.DrilldownFinding;
import com.bcosmos.backend.web.dto.AnalyticsResponses.Hotspot;
import com.bcosmos.backend.web.dto.AnalyticsResponses.Overview;
import com.bcosmos.backend.web.dto.AnalyticsResponses.RuleStat;
import com.bcosmos.backend.web.dto.AnalyticsResponses.TimeToFixBucket;
import com.bcosmos.backend.web.dto.AnalyticsResponses.TrendPoint;
import com.bcosmos.backend.web.dto.PagedResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Phase 5 analytics endpoints (spec v2 section 8, {@code GET /api/analytics/*}).
 *
 * <p>They read the Spark gold tables through {@link AnalyticsService} and never contact Spark: a request that had to
 * wait for a cluster would be a request that fails when the cluster is down, for a number that was already
 * computed.</p>
 *
 * <p>Every list is paginated ({@link Pagination}), and every chart has a drill-down endpoint that takes the same
 * dimension it grouped on, which is what makes "every chart drills down to records" a property of the API rather
 * than of the screen.</p>
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** The snapshot header, the headline numbers and the filters the screen offers. */
    @GetMapping("/overview")
    public Overview overview() {
        return analytics.overview();
    }

    /** Findings per rule and severity, per KLOC. */
    @GetMapping("/rules")
    public PagedResponse<RuleStat> rules(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return analytics.rules(repositoryId, severity, Math.max(page, 0), Pagination.clamp(size));
    }

    /** Finding density per UTC week of the analysis window. */
    @GetMapping("/trends")
    public PagedResponse<TrendPoint> trends(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return analytics.trends(repositoryId, Math.max(page, 0), Pagination.clamp(size));
    }

    /** The commits-until-resolved histogram per rule. */
    @GetMapping("/time-to-fix")
    public PagedResponse<TimeToFixBucket> timeToFix(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return analytics.timeToFix(repositoryId, ruleId, Math.max(page, 0), Pagination.clamp(size));
    }

    /** Ranked files with every component of the triage heuristic. */
    @GetMapping("/hotspots")
    public PagedResponse<Hotspot> hotspots(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return analytics.hotspots(repositoryId, Math.max(page, 0), Pagination.clamp(size));
    }

    /** The dataset card's numbers per split. */
    @GetMapping("/dataset/splits")
    public PagedResponse<DatasetSplit> splits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return analytics.splits(Math.max(page, 0), Pagination.clamp(size));
    }

    /** Which repository is in which split, with its licence. */
    @GetMapping("/dataset/repositories")
    public PagedResponse<DatasetRepository> datasetRepositories(
            @RequestParam(required = false) String split,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return analytics.datasetRepositories(split, Math.max(page, 0), Pagination.clamp(size));
    }

    /**
     * The records behind an aggregate. This is the one endpoint the drill-down of every chart calls: a rule bar
     * passes {@code ruleId}, a hotspot row passes {@code filePath}, a trend point passes {@code from}/{@code to}.
     */
    @GetMapping("/findings")
    public PagedResponse<DrilldownFinding> findings(
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return analytics.findings(repositoryId, ruleId, filePath, severity, from, to, Math.max(page, 0),
                Pagination.clamp(size));
    }
}
