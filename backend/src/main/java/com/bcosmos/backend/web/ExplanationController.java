package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.explanation.FindingExplanationService;
import com.bcosmos.backend.web.dto.ExplanationRequest;
import com.bcosmos.backend.web.dto.FindingExplanationResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Phase 2 AI endpoint (spec v2 section 8: {@code POST /api/findings/{id}/explanations}, "Phase 2 only ... replaced
 * by suggestions in Phase 3").
 *
 * <p>The body is optional: no body, or a body without {@code modelId}, means the registry's default model.</p>
 */
@RestController
@RequestMapping("/api/findings")
public class ExplanationController {

    private final FindingExplanationService explanations;

    public ExplanationController(FindingExplanationService explanations) {
        this.explanations = explanations;
    }

    /** {@code POST /api/findings/{id}/explanations} — explain one stored finding with one model. */
    @PostMapping("/{id}/explanations")
    public FindingExplanationResponse explain(@PathVariable long id,
            @RequestBody(required = false) ExplanationRequest request) {
        return FindingExplanationResponse.of(id, explanations.explain(id, request == null ? null : request.modelId()));
    }
}
