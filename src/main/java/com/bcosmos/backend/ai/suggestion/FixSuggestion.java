package com.bcosmos.backend.ai.suggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The structured answer a model must produce for one finding (spec v2 section 7.3's response schema).
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is a deliberate safety net: a model that adds an extra
 * key should not turn a usable answer into a binding error after the schema check already passed. What is required
 * is enforced by the schema and the {@link ValidationVerdict}, not by the JSON mapper.</p>
 *
 * @param summary            one sentence a reviewer can paste into a review comment
 * @param problemExplanation what the rule is telling us here and why it fired on this code
 * @param evidence           the file and line ranges the explanation rests on; every one is checked to exist
 * @param proposedDiff       a unified diff, applied in memory and never to the repository
 * @param risks              what could go wrong when the change is made
 * @param testsToRun         checks worth running; an empty list when nothing applies
 * @param uncertainties      what the evidence could not settle
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixSuggestion(
        String summary,
        String problemExplanation,
        List<EvidenceRef> evidence,
        String proposedDiff,
        List<String> risks,
        List<String> testsToRun,
        List<String> uncertainties) {

    public FixSuggestion {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        risks = risks == null ? List.of() : List.copyOf(risks);
        testsToRun = testsToRun == null ? List.of() : List.copyOf(testsToRun);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }
}
