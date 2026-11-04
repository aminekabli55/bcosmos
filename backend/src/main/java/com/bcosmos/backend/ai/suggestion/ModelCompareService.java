package com.bcosmos.backend.ai.suggestion;

import com.bcosmos.backend.ai.gateway.ModelRef;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.web.dto.BenchmarkResponses.ModelCompareResponse;
import com.bcosmos.backend.web.dto.BenchmarkResponses.ModelComparisonItem;
import com.bcosmos.backend.web.dto.SuggestionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates side-by-side fix suggestion generation and validation across multiple models (Phase 6, spec section 7.4 & 8).
 */
@Service
public class ModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(ModelCompareService.class);

    private final SuggestionService suggestionService;
    private final ModelRegistry modelRegistry;

    public ModelCompareService(SuggestionService suggestionService, ModelRegistry modelRegistry) {
        this.suggestionService = Objects.requireNonNull(suggestionService, "suggestionService");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
    }

    /**
     * Generates and compares fix suggestions for one finding across the requested models.
     *
     * @param findingId the target finding
     * @param modelIds  list of model registry IDs to compare; if null or empty, compares all enabled chat models
     * @return side-by-side comparison result with verdicts and metrics
     */
    public ModelCompareResponse compareModels(long findingId, List<Long> modelIds) {
        List<ModelRef> targetModels = resolveModels(modelIds);
        List<ModelComparisonItem> items = new ArrayList<>();

        for (ModelRef model : targetModels) {
            try {
                SuggestionService.SuggestionResult result = suggestionService.suggest(findingId, model.id(), null);
                SuggestionResponse response = SuggestionResponse.of(result);
                items.add(ModelComparisonItem.success(
                        model.id(),
                        model.label(),
                        response,
                        result.suggestion().getLatencyMs(),
                        result.suggestion().getTokensIn(),
                        result.suggestion().getTokensOut()
                ));
            } catch (Exception e) {
                log.warn("Failed model comparison for finding {} on model {}: {}", findingId, model.label(), e.getMessage());
                items.add(ModelComparisonItem.failure(model.id(), model.label(), e.getMessage()));
            }
        }

        return new ModelCompareResponse(findingId, items);
    }

    private List<ModelRef> resolveModels(List<Long> modelIds) {
        if (modelIds != null && !modelIds.isEmpty()) {
            return modelIds.stream()
                    .map(modelRegistry::require)
                    .toList();
        }
        // Fall back to all enabled chat models
        return modelRegistry.listing().stream()
                .filter(m -> m.enabled() && m.capability() == LlmModelCapability.CHAT)
                .map(m -> modelRegistry.require(m.id()))
                .toList();
    }
}
