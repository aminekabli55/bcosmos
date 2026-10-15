package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.benchmark.BenchmarkService;
import com.bcosmos.backend.web.dto.BenchmarkResponses.BenchmarkRunResponse;
import com.bcosmos.backend.web.dto.BenchmarkResponses.CreateBenchmarkRunRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Benchmark evaluation REST controller exposing leaderboard and run recordings (Phase 6, spec section 8).
 */
@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * {@code GET /api/benchmarks} — the model evaluation leaderboard ranked by target finding resolved rate.
     */
    @GetMapping
    public List<BenchmarkRunResponse> leaderboard(
            @RequestParam(name = "datasetVersion", required = false) String datasetVersion) {
        if (datasetVersion != null && !datasetVersion.isBlank()) {
            return benchmarkService.getRunsByDataset(datasetVersion);
        }
        return benchmarkService.getLeaderboard();
    }

    /**
     * {@code POST /api/benchmarks} — records a new evaluation benchmark run.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BenchmarkRunResponse recordRun(@Valid @RequestBody CreateBenchmarkRunRequest request) {
        return benchmarkService.recordRun(request);
    }
}
