package com.bcosmos.backend.ai.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bcosmos.backend.ai.gateway.ModelRef;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmProvider;
import com.bcosmos.backend.persistence.SuggestionEntity;
import com.bcosmos.backend.persistence.SuggestionStatus;
import com.bcosmos.backend.web.dto.BenchmarkResponses.ModelCompareResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ModelCompareService side-by-side evaluation (Phase 6, spec section 7.4 & 8)")
class ModelCompareServiceTest {

    private final SuggestionService suggestionService = mock(SuggestionService.class);
    private final ModelRegistry modelRegistry = mock(ModelRegistry.class);
    private final ModelCompareService compareService = new ModelCompareService(suggestionService, modelRegistry);

    @Test
    @DisplayName("compares multiple models side-by-side on one finding")
    void comparesModelsSuccessfully() {
        ModelRef model1 = new ModelRef(1L, LlmProvider.GEMINI, "gemini-2.5-flash", LlmModelKind.HOSTED,
                LlmModelCapability.CHAT, null, 128_000, new BigDecimal("0.000300"), new BigDecimal("0.002500"));
        ModelRef model2 = new ModelRef(2L, LlmProvider.OPENAI_COMPATIBLE, "gpt-4o-mini", LlmModelKind.HOSTED,
                LlmModelCapability.CHAT, null, 128_000, new BigDecimal("0.000150"), new BigDecimal("0.000600"));

        when(modelRegistry.require(1L)).thenReturn(model1);
        when(modelRegistry.require(2L)).thenReturn(model2);

        FixSuggestion fix1 = new FixSuggestion("Fix 1", "Explanation 1", List.of(), "diff 1", List.of(), List.of(), List.of());
        FixSuggestion fix2 = new FixSuggestion("Fix 2", "Explanation 2", List.of(), "diff 2", List.of(), List.of(), List.of());

        ValidationVerdict verdict1 = new ValidationVerdict(true, true, true, true, true, List.of(), List.of());
        ValidationVerdict verdict2 = new ValidationVerdict(true, true, true, true, false,
                List.of(new ValidationVerdict.NewFinding("EmptyCatchBlock", 5, "snippet")), List.of());

        SuggestionEntity entity1 = mock(SuggestionEntity.class);
        when(entity1.getId()).thenReturn(10L);
        when(entity1.getFindingId()).thenReturn(100L);
        when(entity1.getModelId()).thenReturn(1L);
        when(entity1.getPromptVersion()).thenReturn("suggest-fix.v1");
        when(entity1.getStatus()).thenReturn(SuggestionStatus.PROPOSED);
        when(entity1.getCreatedAt()).thenReturn(Instant.now());
        when(entity1.getLatencyMs()).thenReturn(1200L);
        when(entity1.getTokensIn()).thenReturn(450);
        when(entity1.getTokensOut()).thenReturn(120);

        SuggestionEntity entity2 = mock(SuggestionEntity.class);
        when(entity2.getId()).thenReturn(11L);
        when(entity2.getFindingId()).thenReturn(100L);
        when(entity2.getModelId()).thenReturn(2L);
        when(entity2.getPromptVersion()).thenReturn("suggest-fix.v1");
        when(entity2.getStatus()).thenReturn(SuggestionStatus.PROPOSED);
        when(entity2.getCreatedAt()).thenReturn(Instant.now());
        when(entity2.getLatencyMs()).thenReturn(1500L);
        when(entity2.getTokensIn()).thenReturn(480);
        when(entity2.getTokensOut()).thenReturn(140);

        EvidenceBundle bundle = mock(EvidenceBundle.class);
        SuggestionContext context1 = new SuggestionContext("Gemini 2.5 Flash", "text-embedding-004", 5, new SuggestionContext.Index(5, true, 0, 100, 20L), bundle);
        SuggestionContext context2 = new SuggestionContext("GPT-4o Mini", "text-embedding-004", 5, new SuggestionContext.Index(5, true, 0, 100, 20L), bundle);

        when(suggestionService.suggest(eq(100L), eq(1L), isNull()))
                .thenReturn(new SuggestionService.SuggestionResult(entity1, fix1, verdict1, context1, "Gemini 2.5 Flash"));
        when(suggestionService.suggest(eq(100L), eq(2L), isNull()))
                .thenReturn(new SuggestionService.SuggestionResult(entity2, fix2, verdict2, context2, "GPT-4o Mini"));

        ModelCompareResponse response = compareService.compareModels(100L, List.of(1L, 2L));

        assertThat(response.findingId()).isEqualTo(100L);
        assertThat(response.comparisons()).hasSize(2);

        var comp1 = response.comparisons().get(0);
        assertThat(comp1.modelId()).isEqualTo(1L);
        assertThat(comp1.status()).isEqualTo("SUCCESS");
        assertThat(comp1.latencyMs()).isEqualTo(1200L);
        assertThat(comp1.suggestion().verdict().targetFindingGone()).isTrue();

        var comp2 = response.comparisons().get(1);
        assertThat(comp2.modelId()).isEqualTo(2L);
        assertThat(comp2.status()).isEqualTo("SUCCESS");
        assertThat(comp2.latencyMs()).isEqualTo(1500L);
        assertThat(comp2.suggestion().verdict().targetFindingGone()).isFalse();
    }

    @Test
    @DisplayName("handles model error gracefully without failing other models in comparison")
    void handlesIndividualModelFailure() {
        ModelRef model1 = new ModelRef(1L, LlmProvider.GEMINI, "gemini-2.5-flash", LlmModelKind.HOSTED,
                LlmModelCapability.CHAT, null, 128_000, new BigDecimal("0.000300"), new BigDecimal("0.002500"));
        ModelRef model2 = new ModelRef(2L, LlmProvider.OPENAI_COMPATIBLE, "failed-model", LlmModelKind.HOSTED,
                LlmModelCapability.CHAT, null, 128_000, new BigDecimal("0.000150"), new BigDecimal("0.000600"));

        when(modelRegistry.require(1L)).thenReturn(model1);
        when(modelRegistry.require(2L)).thenReturn(model2);

        FixSuggestion fix1 = new FixSuggestion("Fix 1", "Explanation 1", List.of(), "diff 1", List.of(), List.of(), List.of());
        ValidationVerdict verdict1 = new ValidationVerdict(true, true, true, true, true, List.of(), List.of());
        SuggestionEntity entity1 = mock(SuggestionEntity.class);
        when(entity1.getId()).thenReturn(10L);
        when(entity1.getFindingId()).thenReturn(100L);
        when(entity1.getModelId()).thenReturn(1L);
        when(entity1.getPromptVersion()).thenReturn("suggest-fix.v1");
        when(entity1.getStatus()).thenReturn(SuggestionStatus.PROPOSED);
        when(entity1.getCreatedAt()).thenReturn(Instant.now());
        when(entity1.getLatencyMs()).thenReturn(1200L);
        when(entity1.getTokensIn()).thenReturn(450);
        when(entity1.getTokensOut()).thenReturn(120);

        EvidenceBundle bundle = mock(EvidenceBundle.class);
        SuggestionContext context1 = new SuggestionContext("Gemini 2.5 Flash", "text-embedding-004", 5, new SuggestionContext.Index(5, true, 0, 100, 20L), bundle);

        when(suggestionService.suggest(eq(100L), eq(1L), isNull()))
                .thenReturn(new SuggestionService.SuggestionResult(entity1, fix1, verdict1, context1, "Gemini 2.5 Flash"));
        when(suggestionService.suggest(eq(100L), eq(2L), isNull()))
                .thenThrow(new RuntimeException("Rate limit exceeded for provider"));

        ModelCompareResponse response = compareService.compareModels(100L, List.of(1L, 2L));

        assertThat(response.comparisons()).hasSize(2);
        assertThat(response.comparisons().get(0).status()).isEqualTo("SUCCESS");
        assertThat(response.comparisons().get(1).status()).isEqualTo("ERROR");
        assertThat(response.comparisons().get(1).errorMessage()).contains("Rate limit exceeded");
    }
}
