package com.bcosmos.backend.web;

import com.bcosmos.backend.jobs.AnalysisCounts;
import com.bcosmos.backend.jobs.AnalysisRunService;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.repository.RepositoryRegistrationService;
import com.bcosmos.backend.web.dto.AnalysisRunResponse;
import com.bcosmos.backend.web.dto.PagedResponse;
import com.bcosmos.backend.web.dto.RegisterRepositoryRequest;
import com.bcosmos.backend.web.dto.RepositoryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and listing of local repositories, plus starting an analysis (spec v2 section 8, UC1 and UC2).
 *
 * <p>A registered repository is only ever read: no build, no scripts, no hooks (spec section 0 rule 4).</p>
 */
@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryRegistrationService registrations;
    private final AnalysisRunService runs;

    public RepositoryController(RepositoryRegistrationService registrations, AnalysisRunService runs) {
        this.registrations = registrations;
        this.runs = runs;
    }

    /** {@code POST /api/repositories} — register a local repository path. */
    @PostMapping
    public ResponseEntity<RepositoryResponse> register(@Valid @RequestBody RegisterRepositoryRequest request) {
        RepositoryEntity repository = registrations.register(request.name(), request.localPath(),
                RepositorySourceType.LOCAL);
        return ResponseEntity.created(URI.create("/api/repositories/" + repository.getId()))
                .body(RepositoryResponse.of(repository));
    }

    /** {@code GET /api/repositories} — paginated list, newest first. */
    @GetMapping
    public PagedResponse<RepositoryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<RepositoryEntity> repositories = registrations.list(pageable(page, size));
        return PagedResponse.of(repositories, RepositoryResponse::of);
    }

    /** {@code GET /api/repositories/{id}} — one repository. */
    @GetMapping("/{id}")
    public RepositoryResponse get(@PathVariable long id) {
        return RepositoryResponse.of(registrations.require(id));
    }

    /** {@code POST /api/repositories/{id}/analyses} — start an analysis and return the run to follow. */
    @PostMapping("/{id}/analyses")
    public ResponseEntity<AnalysisRunResponse> startAnalysis(@PathVariable long id) {
        AnalysisRunEntity run = runs.start(id);
        AnalysisRunResponse response = AnalysisRunResponse.of(run, new AnalysisCounts(0, 0), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/analyses/" + run.getId()))
                .body(response);
    }

    /** {@code GET /api/repositories/{id}/analyses} — run history of one repository, newest first. */
    @GetMapping("/{id}/analyses")
    public PagedResponse<AnalysisRunResponse> listAnalyses(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AnalysisRunEntity> pageOfRuns = runs.listByRepository(id, pageable(page, size));
        List<AnalysisRunResponse> items = pageOfRuns.getContent().stream()
                .map(run -> AnalysisRunResponse.of(run, runs.counts(run.getId()), runs.jobOf(run.getId()).orElse(null)))
                .toList();
        return new PagedResponse<>(items, pageOfRuns.getNumber(), pageOfRuns.getSize(),
                pageOfRuns.getTotalElements(), pageOfRuns.getTotalPages(), pageOfRuns.hasNext());
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Pagination.clamp(size));
    }
}
