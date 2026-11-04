package com.bcosmos.backend.ai.explanation;

import com.bcosmos.backend.ai.gateway.LlmUsage;
import com.bcosmos.backend.ai.gateway.ModelRef;
import java.util.List;
import java.util.Objects;

/**
 * An explanation plus the accounting of producing it.
 *
 * @param explanation      the validated model answer
 * @param model            which model answered
 * @param promptVersion    the versioned prompt used, stored with the call
 * @param usage            tokens, latency, attempts and estimated cost
 * @param rejectedAttempts what the first answer got wrong, when the schema forced a retry; empty otherwise
 */
public record FindingExplanationResult(
        FindingExplanation explanation,
        ModelRef model,
        String promptVersion,
        LlmUsage usage,
        List<String> rejectedAttempts) {

    public FindingExplanationResult {
        Objects.requireNonNull(explanation, "explanation");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(usage, "usage");
        rejectedAttempts = List.copyOf(rejectedAttempts);
    }
}
