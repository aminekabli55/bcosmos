package com.bcosmos.backend.ai.explanation;

import java.util.List;

/**
 * What the model must return for one finding, matching
 * {@code prompts/explain-finding.v1.json} (spec v2 sections 7.1 and 9).
 *
 * <p>Evidence is part of the answer: an explanation without references is not usable for a review comment, so
 * {@code references} is required by the schema.</p>
 *
 * @param summary       one sentence a reviewer can paste
 * @param explanation   what the rule means here and what a fix would touch
 * @param confidence    {@code LOW}, {@code MEDIUM} or {@code HIGH}, as the model judges its own evidence
 * @param references    file and line ranges the explanation rests on
 * @param testsToRun    checks worth running, may be empty
 * @param uncertainties what the model could not tell from the evidence, may be empty
 */
public record FindingExplanation(
        String summary,
        String explanation,
        String confidence,
        List<Reference> references,
        List<String> testsToRun,
        List<String> uncertainties) {

    public FindingExplanation {
        references = references == null ? List.of() : List.copyOf(references);
        testsToRun = testsToRun == null ? List.of() : List.copyOf(testsToRun);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }

    /**
     * One piece of evidence.
     *
     * @param file      repository-relative path, the only kind of path this project ever handles
     * @param startLine first line the claim rests on
     * @param endLine   last line, or {@code null} when the claim covers a single line
     * @param why       what this evidence shows
     */
    public record Reference(String file, Integer startLine, Integer endLine, String why) {
    }
}
