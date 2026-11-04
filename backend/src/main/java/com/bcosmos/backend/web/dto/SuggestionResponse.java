package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.ai.suggestion.EvidenceRef;
import com.bcosmos.backend.ai.suggestion.FixSuggestion;
import com.bcosmos.backend.ai.suggestion.SuggestionContext;
import com.bcosmos.backend.ai.suggestion.SuggestionService;
import com.bcosmos.backend.ai.suggestion.ValidationVerdict;
import com.bcosmos.backend.persistence.SuggestionStatus;
import java.time.Instant;
import java.util.List;

/**
 * One suggestion as the AI Review screen consumes it (spec v2 sections 8 and 9).
 *
 * <p>The nesting mirrors what a reviewer reads top to bottom: what the model said ({@code answer}), what BCosmos
 * checked about it ({@code verdict}), what it was shown ({@code retrieval}), and what it cost. Every field is a
 * value type or a scalar — no entity, and no credential, ever reaches this shape.</p>
 *
 * <p>{@code verdict.allChecksPassed} is a summary of the automatic checks, never an approval: the human decision is
 * {@code status} (spec v2 section 1.3).</p>
 */
public record SuggestionResponse(
        long id,
        long findingId,
        Long modelId,
        String modelLabel,
        String promptVersion,
        SuggestionStatus status,
        Instant createdAt,
        Instant reviewedAt,
        long latencyMs,
        int tokensIn,
        int tokensOut,
        Answer answer,
        Verdict verdict,
        Retrieval retrieval) {

    public static SuggestionResponse of(SuggestionService.SuggestionResult result) {
        var suggestion = result.suggestion();
        return new SuggestionResponse(
                result.id(),
                suggestion.getFindingId(),
                suggestion.getModelId(),
                result.modelLabel(),
                suggestion.getPromptVersion(),
                suggestion.getStatus(),
                suggestion.getCreatedAt(),
                suggestion.getReviewedAt(),
                suggestion.getLatencyMs(),
                suggestion.getTokensIn(),
                suggestion.getTokensOut(),
                Answer.of(result.answer()),
                Verdict.of(result.verdict()),
                Retrieval.of(result.context()));
    }

    /** What the model answered, with its citations listed separately so each one can be read against the verdict. */
    public record Answer(
            String summary,
            String problemExplanation,
            List<Citation> citations,
            String proposedDiff,
            List<String> risks,
            List<String> testsToRun,
            List<String> uncertainties) {

        static Answer of(FixSuggestion suggestion) {
            return new Answer(
                    suggestion.summary(),
                    suggestion.problemExplanation(),
                    suggestion.evidence().stream().map(Citation::of).toList(),
                    suggestion.proposedDiff(),
                    suggestion.risks(),
                    suggestion.testsToRun(),
                    suggestion.uncertainties());
        }
    }

    /** A citation the model made; whether it survived the check is in {@code verdict.evidenceOk}. */
    public record Citation(String file, Integer startLine, Integer endLine, String why) {

        static Citation of(EvidenceRef reference) {
            return new Citation(reference.file(), reference.startLine(), reference.endLine(), reference.why());
        }
    }

    /** The automatic verdict: what was checked, what passed, and what could not be checked at all. */
    public record Verdict(
            boolean schemaOk,
            boolean evidenceOk,
            boolean diffApplies,
            boolean parses,
            boolean targetFindingGone,
            boolean allChecksPassed,
            List<NewFinding> newFindings,
            List<String> notes) {

        static Verdict of(ValidationVerdict verdict) {
            return new Verdict(
                    verdict.schemaOk(),
                    verdict.evidenceOk(),
                    verdict.diffApplies(),
                    verdict.parses(),
                    verdict.targetFindingGone(),
                    verdict.allChecksPassed(),
                    verdict.newFindings().stream()
                            .map(finding -> new NewFinding(finding.ruleId(), finding.startLine(), finding.message()))
                            .toList(),
                    verdict.notes());
        }
    }

    /** A finding the proposed patch would introduce in the file. */
    public record NewFinding(String ruleId, int startLine, String message) {
    }

    /** What retrieval showed the model, including the notes saying what it could not provide. */
    public record Retrieval(
            String embedModel,
            int chunksConsidered,
            int estimatedTokens,
            boolean truncated,
            String ruleDescription,
            List<String> notes,
            List<Item> items,
            Index index) {

        static Retrieval of(SuggestionContext context) {
            EvidenceBundle bundle = context.bundle();
            return new Retrieval(
                    context.embedModel(),
                    context.chunksConsidered(),
                    bundle.estimatedTokens(),
                    bundle.truncated(),
                    bundle.ruleDescription(),
                    bundle.notes(),
                    bundle.items().stream().map(Item::of).toList(),
                    Index.of(context.index()));
        }
    }

    /** One piece of evidence, with the citation and the reason it is there. */
    public record Item(
            EvidenceBundle.EvidenceKind kind,
            String file,
            int startLine,
            int endLine,
            String qualifiedName,
            String text,
            Double similarity,
            int estimatedTokens) {

        static Item of(EvidenceBundle.EvidenceItem item) {
            return new Item(item.kind(), item.file(), item.startLine(), item.endLine(), item.qualifiedName(),
                    item.text(), item.similarity(), item.estimatedTokens());
        }
    }

    /** What the run's index build did, so the token and cost figures are not a surprise later. */
    public record Index(int chunks, boolean reused, long symbolsWithoutSource, int tokensIn, long latencyMs) {

        static Index of(SuggestionContext.Index index) {
            return new Index(index.chunks(), index.reused(), index.symbolsWithoutSource(), index.tokensIn(),
                    index.latencyMs());
        }
    }
}
