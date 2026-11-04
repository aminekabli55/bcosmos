package com.bcosmos.backend.ai.suggestion;

import com.bcosmos.backend.ai.gateway.LlmUsage;
import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.ai.rag.EmbeddingService;
import java.util.List;

/**
 * How one suggestion was produced, kept next to it so the review screen can reopen it unchanged (table
 * {@code suggestion}, column {@code retrieval_json}).
 *
 * <p>Without it, reopening a suggestion would mean calling a provider again and re-embedding the run, and the
 * evidence shown would be whatever retrieval returns today rather than what the model actually saw. Storing it is
 * the difference between an auditable suggestion and a re-enactment.</p>
 *
 * @param modelLabel        provider and model name as they were at the time
 * @param embedModel        {@code code_chunk.embed_model} used to index the run
 * @param chunksConsidered  how many chunks the run had
 * @param index             what the index build did (or that it was reused)
 * @param items             the evidence the model was shown, in the order it was shown
 */
public record SuggestionContext(
        String modelLabel,
        String embedModel,
        int chunksConsidered,
        Index index,
        EvidenceBundle bundle) {

    /** What building the index cost, so the review screen can show it with the rest of the usage. */
    public record Index(int chunks, boolean reused, long symbolsWithoutSource, int tokensIn, long latencyMs) {

        static Index of(EmbeddingService.EmbeddingIndex index) {
            LlmUsage usage = index.usage();
            return new Index(index.chunks(), index.reused(), index.symbolsWithoutSource(),
                    usage == null ? 0 : usage.tokensIn(),
                    usage == null ? 0 : usage.latencyMs());
        }
    }

    public static SuggestionContext of(String modelLabel, EmbeddingService.IndexedRun indexed,
            EvidenceBundle bundle) {
        return new SuggestionContext(modelLabel, indexed.index().embedModel(), indexed.drafts().size(),
                Index.of(indexed.index()), bundle);
    }

    /** The evidence items, for a caller that only wants the list. */
    public List<EvidenceBundle.EvidenceItem> items() {
        return bundle.items();
    }
}
