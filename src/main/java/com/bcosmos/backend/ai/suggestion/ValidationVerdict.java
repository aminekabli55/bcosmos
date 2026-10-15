package com.bcosmos.backend.ai.suggestion;

import java.util.List;
import java.util.Objects;

/**
 * What BCosmos checked about a suggestion before showing it (spec v2 section 7.3).
 *
 * <p>This is the part of the project that makes model quality measurable instead of a matter of taste: the diff
 * must apply to the analysed revision, the patched file must parse, the finding the suggestion was asked about must
 * be gone afterwards, and no new finding may have appeared. It never proves the behaviour is preserved - that is
 * stated as a limit, not hidden.</p>
 *
 * @param schemaOk           the answer passed the JSON schema, if it needed no retry at all
 * @param evidenceOk         every cited file exists in the run and every cited line range exists in the file
 * @param diffApplies        the diff is well formed and applies to the stored revision
 * @param parses             the patched file still parses
 * @param targetFindingGone  the finding the suggestion was asked about is no longer reported for that file
 * @param newFindings        findings the patch introduced in the file, with the rule and the line
 * @param notes              what could not be checked, said plainly (no compile sandbox, no test run)
 */
public record ValidationVerdict(
        boolean schemaOk,
        boolean evidenceOk,
        boolean diffApplies,
        boolean parses,
        boolean targetFindingGone,
        List<NewFinding> newFindings,
        List<String> notes) {

    /** Compilation is not verified: nothing in this phase has a sandbox to build a patch in. */
    public static final String COMPILE_NOT_VERIFIED =
            "the patch was not compiled: no build sandbox is used, so 'compile not verified' is the honest state";

    public static final String TESTS_NOT_RUN =
            "no test was run: BCosmos never executes code from an analyzed repository (spec section 10)";

    public ValidationVerdict {
        newFindings = List.copyOf(newFindings);
        notes = List.copyOf(notes);
    }

    /**
     * Whether every automatic check passed.
     *
     * <p>It is not an approval. A passing verdict means "worth a human's time", and the human decision is the
     * suggestion's review status (spec v2 section 1.3: patches are never applied automatically).</p>
     */
    public boolean allChecksPassed() {
        return schemaOk && evidenceOk && diffApplies && parses && targetFindingGone && newFindings.isEmpty();
    }

    /** A finding the patch would introduce. */
    public record NewFinding(String ruleId, int startLine, String message) {

        public NewFinding {
            Objects.requireNonNull(ruleId, "ruleId");
        }
    }
}
