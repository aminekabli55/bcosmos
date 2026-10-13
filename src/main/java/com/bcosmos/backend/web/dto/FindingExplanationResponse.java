package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.explanation.FindingExplanation;
import com.bcosmos.backend.ai.explanation.FindingExplanationResult;
import com.bcosmos.backend.persistence.LlmProvider;
import java.util.List;

/**
 * One explained finding (spec v2 section 8, {@code POST /api/findings/{id}/explanations}).
 *
 * <p>{@code rejectedAttempts} is part of the response on purpose: the phase's acceptance criterion is that a schema
 * violation is retried <em>and both attempts are logged</em>, so the API says a retry happened and what the first
 * answer got wrong instead of hiding it.</p>
 *
 * @param modelName        which model answered
 * @param promptVersion    versioned prompt used, the same value the usage row stores
 * @param rejectedAttempts schema problems of the first answer; empty when it validated immediately
 */
public record FindingExplanationResponse(
        long findingId,
        long modelId,
        String modelName,
        LlmProvider provider,
        String promptVersion,
        String summary,
        String explanation,
        String confidence,
        List<FindingExplanation.Reference> references,
        List<String> testsToRun,
        List<String> uncertainties,
        List<String> rejectedAttempts,
        LlmUsageResponse usage) {

    public static FindingExplanationResponse of(long findingId, FindingExplanationResult result) {
        FindingExplanation explanation = result.explanation();
        return new FindingExplanationResponse(
                findingId,
                result.model().id(),
                result.model().modelName(),
                result.model().provider(),
                result.promptVersion(),
                explanation.summary(),
                explanation.explanation(),
                explanation.confidence(),
                explanation.references(),
                explanation.testsToRun(),
                explanation.uncertainties(),
                result.rejectedAttempts(),
                LlmUsageResponse.of(result.usage()));
    }
}
