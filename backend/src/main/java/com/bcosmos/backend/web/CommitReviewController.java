package com.bcosmos.backend.web;

import com.bcosmos.backend.commit.CommitReviewService;
import com.bcosmos.backend.web.dto.CommitResponses.CommitListResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CommitReviewResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CreateCommitReviewRequest;
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
 * REST interface for the Commit Review feature (spec v2 section 7.6).
 *
 * <ul>
 *   <li>{@code GET  /api/repositories/{id}/commits} – list recent commits (max 100)</li>
 *   <li>{@code POST /api/repositories/{id}/commit-reviews} – create a 3-layer review</li>
 *   <li>{@code GET  /api/commit-reviews/{id}} – retrieve an existing review</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class CommitReviewController {

    private final CommitReviewService service;

    public CommitReviewController(CommitReviewService service) {
        this.service = service;
    }

    /**
     * Lists recent commits for a repository together with any existing commit review ID.
     *
     * @param id    repository ID
     * @param limit max commits to return, default 30, max 100
     */
    @GetMapping("/repositories/{id}/commits")
    public ResponseEntity<CommitListResponse> listCommits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 100);
        return ResponseEntity.ok(service.listCommits(id, safeLimit));
    }

    /**
     * Creates a three-layer commit review (facts → risk signals → optional AI summary).
     * Returns 201 Created with the full review body.
     */
    @PostMapping("/repositories/{id}/commit-reviews")
    public ResponseEntity<CommitReviewResponse> createReview(
            @PathVariable Long id,
            @RequestBody CreateCommitReviewRequest request) {
        CommitReviewResponse response = service.createReview(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves an existing commit review by its primary key.
     */
    @GetMapping("/commit-reviews/{id}")
    public ResponseEntity<CommitReviewResponse> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(service.getReview(id));
    }
}
