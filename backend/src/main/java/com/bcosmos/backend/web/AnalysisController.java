package com.bcosmos.backend.web;

import com.bcosmos.backend.jobs.AnalysisRunService;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.web.dto.AnalysisRunResponse;
import com.bcosmos.backend.web.dto.FileRecordResponse;
import com.bcosmos.backend.web.dto.FindingResponse;
import com.bcosmos.backend.web.dto.PagedResponse;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Severity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Run status and its evidence (spec v2 section 8: {@code GET /api/analyses/{id}}, {@code .../findings}).
 *
 * <p>{@code GET /api/analyses/{id}/files} is an addition to the spec's endpoint table: Phase 1 requires that a file
 * which failed to parse is visible, and a counter alone does not say which file it was.</p>
 */
@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    /** Severity first, then rule and position: the order a reviewer reads a findings table in. */
    private static final Sort FINDING_ORDER = Sort.by(
            Sort.Order.desc("severityRank"), Sort.Order.asc("ruleId"), Sort.Order.asc("startLine"));

    private final AnalysisRunService runs;
    private final FindingJpaRepository findings;
    private final FileRecordJpaRepository fileRecords;

    public AnalysisController(AnalysisRunService runs, FindingJpaRepository findings,
            FileRecordJpaRepository fileRecords) {
        this.runs = runs;
        this.findings = findings;
        this.fileRecords = fileRecords;
    }

    /** {@code GET /api/analyses/{id}} — status, stage, counters, tool versions and error details. */
    @GetMapping("/{id}")
    public AnalysisRunResponse get(@PathVariable long id) {
        AnalysisRunEntity run = runs.require(id);
        return AnalysisRunResponse.of(run, runs.counts(id), runs.jobOf(id).orElse(null));
    }

    /**
     * {@code GET /api/analyses/{id}/findings} — filtered, paginated findings.
     *
     * @param severity optional normalized severity filter
     * @param rule     optional exact rule id filter
     * @param file     optional case-insensitive substring of the repository-relative path
     */
    @GetMapping("/{id}/findings")
    public PagedResponse<FindingResponse> findings(
            @PathVariable long id,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String rule,
            @RequestParam(required = false) String file,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        runs.require(id);
        String filePattern = file == null || file.isBlank() ? null : "%" + file.trim().toLowerCase() + "%";
        Page<FindingEntity> pageOfFindings = findings.search(id, severity, blankToNull(rule), filePattern,
                Pagination.of(page, size, FINDING_ORDER));
        Map<Long, String> paths = filePathsOf(id, pageOfFindings.getContent());
        List<FindingResponse> items = pageOfFindings.getContent().stream()
                .map(finding -> FindingResponse.of(finding, paths.get(finding.getFileId())))
                .toList();
        return new PagedResponse<>(items, pageOfFindings.getNumber(), pageOfFindings.getSize(),
                pageOfFindings.getTotalElements(), pageOfFindings.getTotalPages(), pageOfFindings.hasNext());
    }

    /** {@code GET /api/analyses/{id}/files} — the files of a run, optionally only the failures. */
    @GetMapping("/{id}/files")
    public PagedResponse<FileRecordResponse> files(
            @PathVariable long id,
            @RequestParam(required = false) ParseStatus parseStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        runs.require(id);
        Page<FileRecordEntity> pageOfFiles = parseStatus == null
                ? fileRecords.findByRunIdOrderByRelativePathAsc(id, Pagination.of(page, size, Sort.unsorted()))
                : fileRecords.findByRunIdAndParseStatusOrderByRelativePathAsc(id, parseStatus,
                        Pagination.of(page, size, Sort.unsorted()));
        return PagedResponse.of(pageOfFiles, FileRecordResponse::of);
    }

    /** Resolves the relative paths of one page of findings with a single query, never with a lazy association. */
    private Map<Long, String> filePathsOf(long runId, List<FindingEntity> pageOfFindings) {
        List<Long> fileIds = pageOfFindings.stream().map(FindingEntity::getFileId).distinct().toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileRecords.findByRunIdAndIdIn(runId, fileIds).stream()
                .collect(Collectors.toMap(FileRecordEntity::getId, FileRecordEntity::getRelativePath,
                        (first, second) -> first, LinkedHashMap::new));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
