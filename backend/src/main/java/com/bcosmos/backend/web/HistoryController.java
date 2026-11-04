package com.bcosmos.backend.web;

import com.bcosmos.backend.history.HistoryService;
import com.bcosmos.backend.web.dto.HistoryResponses.SnapshotComparisonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for snapshot comparison and finding transitions (Phase 8, spec section 8).
 */
@RestController
@RequestMapping("/api")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/analyses/compare")
    public SnapshotComparisonResponse compareSnapshots(
            @RequestParam("from") Long fromRunId,
            @RequestParam("to") Long toRunId) {
        return historyService.compareSnapshots(fromRunId, toRunId);
    }
}
