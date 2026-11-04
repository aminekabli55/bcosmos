package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.suggestion.ModelCompareService;
import com.bcosmos.backend.ai.suggestion.SuggestionService;
import com.bcosmos.backend.web.dto.BenchmarkResponses.ModelCompareRequest;
import com.bcosmos.backend.web.dto.BenchmarkResponses.ModelCompareResponse;
import com.bcosmos.backend.web.dto.PagedResponse;
import com.bcosmos.backend.web.dto.ReviewSuggestionRequest;
import com.bcosmos.backend.web.dto.SuggestionRequest;
import com.bcosmos.backend.web.dto.SuggestionResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Phase 3 and Phase 6 AI suggestion endpoints (spec v2 section 8:
 * {@code POST /api/findings/{id}/suggestions}, {@code POST /api/findings/{id}/compare-models},
 * {@code POST /api/suggestions/{id}/review}).
 *
 * <p>{@code GET /api/findings/{id}/suggestions} and {@code GET /api/suggestions/{id}} are additions: the review
 * screen has to reopen a stored suggestion, and a page that loses its state should not have to pay for another
 * model call to show what it was already told. Both are paginated like every other list endpoint.</p>
 */
@RestController
@RequestMapping("/api")
public class SuggestionController {

    private final SuggestionService suggestions;
    private final ModelCompareService modelCompareService;

    public SuggestionController(SuggestionService suggestions, ModelCompareService modelCompareService) {
        this.suggestions = suggestions;
        this.modelCompareService = modelCompareService;
    }

    /** {@code POST /api/findings/{id}/compare-models} — evaluate multiple models side-by-side on one finding. */
    @PostMapping("/findings/{id}/compare-models")
    public ModelCompareResponse compareModels(
            @PathVariable long id,
            @RequestBody(required = false) ModelCompareRequest request) {
        List<Long> modelIds = request == null ? null : request.modelIds();
        return modelCompareService.compareModels(id, modelIds);
    }

    /** {@code POST /api/findings/{id}/suggestions} — ask one model for a validated fix suggestion. */
    @PostMapping("/findings/{id}/suggestions")
    public SuggestionResponse suggest(@PathVariable long id,
            @RequestBody(required = false) SuggestionRequest request) {
        return SuggestionResponse.of(suggestions.suggest(id,
                request == null ? null : request.modelId(),
                request == null ? null : request.embeddingModelId()));
    }

    /** {@code GET /api/findings/{id}/suggestions} — the suggestions already stored for a finding, oldest first. */
    @GetMapping("/findings/{id}/suggestions")
    public PagedResponse<SuggestionResponse> history(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.of(suggestions.history(id, Pagination.of(page, size, Sort.unsorted())),
                SuggestionResponse::of);
    }

    /** {@code GET /api/suggestions/{id}} — one stored suggestion with its verdict and the evidence it was given. */
    @GetMapping("/suggestions/{id}")
    public SuggestionResponse get(@PathVariable long id) {
        return SuggestionResponse.of(suggestions.get(id));
    }

    /** {@code POST /api/suggestions/{id}/review} — accept, reject or defer, and move the finding's triage state. */
    @PostMapping("/suggestions/{id}/review")
    public SuggestionResponse review(@PathVariable long id,
            @jakarta.validation.Valid @RequestBody ReviewSuggestionRequest request) {
        return SuggestionResponse.of(suggestions.review(id, request.status()));
    }
}
