package com.bcosmos.backend.ai.rag;

import com.bcosmos.backend.ai.gateway.AiEnablement;
import com.bcosmos.backend.ai.gateway.EmbedRequest;
import com.bcosmos.backend.ai.gateway.EmbedResult;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.LlmDisabledException;
import com.bcosmos.backend.ai.gateway.LlmUsage;
import com.bcosmos.backend.ai.gateway.ModelRef;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.ai.gateway.RateLimitPatience;
import com.bcosmos.backend.config.RetrievalProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the vector index of one run: chunk the symbols, embed the chunks, store the vectors (spec v2 section 7.2).
 *
 * <p>The index is built on demand, the first time a finding of that run needs retrieval, and it is resumable: each
 * batch is written as soon as the provider answers, and a build skips every chunk whose stored text is unchanged.
 * A rate limit or a restart therefore costs the batch that failed, not the whole index (ADR 0044).</p>
 *
 * <p>One build is also bounded: {@code bcosmos.retrieval.index-texts-per-call} texts at most are sent, so a
 * suggestion on a large repository answers in bounded time instead of holding the request for the dozen minutes a
 * whole 1,600-chunk index takes on a free tier. The rest of the index follows with the next suggestion, and
 * retrieval uses whatever is stored by then — a partial index is a smaller evidence bundle, never a wrong one.</p>
 *
 * <p>No transaction is held across a provider call: each batch is embedded, then written in its own short
 * transaction, with no database connection open while waiting.</p>
 *
 * <p>A build is a sequence rather than one answer, so it sits out the rate limits it can afford to: each batch is
 * wrapped in a {@link RateLimitPatience}, which sleeps what the provider asked for and retries that batch, up to
 * {@code bcosmos.retrieval.rate-limit-patience} for the build as a whole (ADR 0041). The per-call budget stays what
 * it was — one call still fails fast.</p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AiEnablement enablement;
    private final ModelRegistry models;
    private final LlmClient llm;
    private final RunSources sources;
    private final SymbolChunker chunker;
    private final CodeChunkStore store;
    private final RetrievalProperties properties;

    public EmbeddingService(AiEnablement enablement, ModelRegistry models, LlmClient llm, RunSources sources,
            SymbolChunker chunker, CodeChunkStore store, RetrievalProperties properties) {
        this.enablement = enablement;
        this.models = models;
        this.llm = llm;
        this.sources = sources;
        this.chunker = chunker;
        this.store = store;
        this.properties = properties;
    }

    /**
     * Makes sure the run has an index for the given embedding model and returns what was indexed.
     *
     * @param runId   analysis run whose symbols are chunked
     * @param modelId embedding registry row, or {@code null} for the registry's default embedding model
     * @throws EmbeddingDimensionException when the model's declared width is not the configured index width
     */
    public IndexedRun ensureIndex(long runId, Long modelId) {
        return ensureIndex(runId, modelId, null);
    }

    /**
     * Makes sure the run has an index, embedding at most one call's budget of what is missing.
     *
     * @param preferredFile file whose chunks are embedded first, so the finding that triggered the build gets its
     *                      own file's neighbours before the rest of the repository (may be {@code null})
     */
    public IndexedRun ensureIndex(long runId, Long modelId, String preferredFile) {
        // The master switch is checked before the registry, so a switched-off layer answers "AI is switched off"
        // rather than complaining about a model the user cannot be expected to have enabled yet.
        if (!enablement.enabled()) {
            throw new LlmDisabledException();
        }
        ModelRef model = models.requireEmbeddingModel(modelId);
        Integer declared = model.embeddingDimension();
        if (declared != null && declared != properties.embeddingDimension()) {
            throw new EmbeddingDimensionException("embedding model " + model.label() + " produces " + declared
                    + "-dimensional vectors but the index is " + properties.embeddingDimension()
                    + "-dimensional (bcosmos.retrieval.embedding-dimension); enable an embedding model of the same"
                    + " width, or change the setting and re-apply the migration");
        }

        String embedModel = embedModelOf(model);
        List<CodeChunkDraft> drafts = chunker.chunks(sources.sourcesOf(runId));
        // Drop rows this plan no longer has, so a stale chunk cannot make a partial index look complete.
        store.prune(runId, embedModel, drafts.stream().map(CodeChunkDraft::symbolId).toList());

        Map<Long, String> stored = store.storedTexts(runId, embedModel);
        List<CodeChunkDraft> missing = ordered(drafts.stream()
                .filter(draft -> !draft.text().equals(stored.get(draft.symbolId())))
                .toList(), preferredFile);
        int budget = properties.indexTextsPerCall() <= 0 ? Integer.MAX_VALUE : properties.indexTextsPerCall();
        List<CodeChunkDraft> toEmbed = missing.subList(0, Math.min(budget, missing.size()));

        Tokens tokens = new Tokens();
        // One patience per build, not one per call: it is the index build as a whole that may sit out a few quota
        // windows, and the gateway's per-call budget is deliberately too short to absorb one (ADR 0041).
        RateLimitPatience patience = new RateLimitPatience(properties.rateLimitPatience(),
                RateLimitPatience.Sleeper.THREAD);
        for (int start = 0; start < toEmbed.size(); start += properties.embedBatchSize()) {
            List<CodeChunkDraft> batch = toEmbed.subList(start, Math.min(start + properties.embedBatchSize(),
                    toEmbed.size()));
            List<String> texts = batch.stream().map(CodeChunkDraft::text).toList();
            EmbedResult result = patience.call(() -> llm.embed(new EmbedRequest(model.id(), texts)));
            if (result.vectors().size() != batch.size()) {
                // The gateway checks the width; the count is checked here, because a provider that drops a text
                // would silently shift every later vector onto the wrong chunk.
                throw new IllegalStateException(model.label() + " returned " + result.vectors().size()
                        + " vector(s) for " + batch.size() + " chunk(s)");
            }
            List<CodeChunkStore.EmbeddedChunk> rows = new ArrayList<>(batch.size());
            for (int index = 0; index < batch.size(); index++) {
                rows.add(new CodeChunkStore.EmbeddedChunk(runId, batch.get(index), embedModel,
                        result.vectors().get(index)));
            }
            // Written now, not after the loop: a rate limit two batches later must not throw this batch away.
            store.upsertAll(runId, embedModel, rows);
            tokens.add(result.usage());
        }

        int storedCount = (int) store.count(runId, embedModel);
        boolean complete = storedCount == drafts.size();
        if (!toEmbed.isEmpty()) {
            log.info("indexed {} of {} chunk(s) of run {} with {} in {} embedding call(s){}", storedCount,
                    drafts.size(), runId, embedModel, tokens.calls(), complete ? ""
                            : "; the next request continues with the rest");
        } else {
            log.debug("reusing {} chunk(s) of run {} for {}", storedCount, runId, embedModel);
        }
        return new IndexedRun(drafts, new EmbeddingIndex(runId, model, embedModel, storedCount,
                toEmbed.isEmpty() && !drafts.isEmpty(), tokens.usage(), skippedSymbols(runId)));
    }

    /** Puts the chunks of one file first, keeping the plan's order inside each group. */
    private static List<CodeChunkDraft> ordered(List<CodeChunkDraft> drafts, String preferredFile) {
        if (preferredFile == null || drafts.isEmpty()) {
            return drafts;
        }
        List<CodeChunkDraft> ordered = new ArrayList<>(drafts.size());
        for (CodeChunkDraft draft : drafts) {
            if (draft.relativePath().equals(preferredFile)) {
                ordered.add(draft);
            }
        }
        for (CodeChunkDraft draft : drafts) {
            if (!draft.relativePath().equals(preferredFile)) {
                ordered.add(draft);
            }
        }
        return ordered;
    }

    /**
     * The value stored in {@code code_chunk.embed_model}: provider code and model name, so it survives a registry
     * edit.
     *
     * <p>It is also the memo key that decides whether vectors can be reused, which is why it names the provider
     * code rather than a provider id: the same model reached through a renamed row is still the same vectors.</p>
     */
    public static String embedModelOf(ModelRef model) {
        return model.label();
    }

    private long skippedSymbols(long runId) {
        long skipped = sources.symbolsWithoutSource(runId);
        return Math.max(0, skipped);
    }

    /**
     * One run's index together with the chunks it was built from.
     *
     * <p>Both travel together on purpose: retrieval picks the enclosing declaration from these exact chunks, so it
     * cannot disagree with what was embedded.</p>
     */
    public record IndexedRun(List<CodeChunkDraft> drafts, EmbeddingIndex index) {

        public IndexedRun {
            drafts = List.copyOf(drafts);
        }
    }

    /** What one index build produced. */
    public record EmbeddingIndex(
            long runId,
            ModelRef model,
            String embedModel,
            int chunks,
            boolean reused,
            LlmUsage usage,
            long symbolsWithoutSource) {

        /** Whether the run has anything to retrieve from at all. */
        public boolean empty() {
            return chunks == 0;
        }
    }

    /** Adds up the usage of several embedding calls into one figure for the index build. */
    private static final class Tokens {

        private int count;
        private int tokensIn;
        private long latencyMs;
        private BigDecimal cost = BigDecimal.ZERO;

        private void add(LlmUsage usage) {
            count++;
            tokensIn += usage.tokensIn();
            latencyMs += usage.latencyMs();
            cost = cost.add(usage.estimatedCostUsd());
        }

        private int calls() {
            return count;
        }

        private LlmUsage usage() {
            return count == 0 ? null : new LlmUsage(tokensIn, 0, latencyMs, count, cost);
        }
    }
}
