package com.bcosmos.backend.ai.rag;

import com.bcosmos.backend.ai.gateway.AiEnablement;
import com.bcosmos.backend.ai.gateway.EmbedRequest;
import com.bcosmos.backend.ai.gateway.EmbedResult;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.LlmDisabledException;
import com.bcosmos.backend.ai.gateway.LlmUsage;
import com.bcosmos.backend.ai.gateway.ModelRef;
import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.config.RetrievalProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the vector index of one run: chunk the symbols, embed the chunks, store the vectors (spec v2 section 7.2).
 *
 * <p>The index is built on demand, the first time a finding of that run needs retrieval, and it is idempotent: if
 * the run already holds one chunk per chunk-source for the same embedding model, nothing is embedded again. That
 * matters because embedding is the one part of this flow that costs money per call.</p>
 *
 * <p>No transaction is held across a provider call: the chunks are embedded first, and the rows are replaced in
 * one short transaction afterwards. A provider failure therefore cannot leave a half-written index behind.</p>
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
        long stored = store.count(runId, embedModel);
        if (stored == drafts.size() && stored > 0) {
            log.debug("reusing {} chunk(s) of run {} for {}", stored, runId, embedModel);
            return new IndexedRun(drafts,
                    new EmbeddingIndex(runId, model, embedModel, (int) stored, true, null, skippedSymbols(runId)));
        }
        if (drafts.isEmpty()) {
            store.replaceAll(runId, embedModel, List.of());
            return new IndexedRun(drafts,
                    new EmbeddingIndex(runId, model, embedModel, 0, false, null, skippedSymbols(runId)));
        }

        List<CodeChunkStore.EmbeddedChunk> embedded = new ArrayList<>(drafts.size());
        Tokens tokens = new Tokens();
        for (int start = 0; start < drafts.size(); start += properties.embedBatchSize()) {
            List<CodeChunkDraft> batch = drafts.subList(start, Math.min(start + properties.embedBatchSize(),
                    drafts.size()));
            EmbedResult result = llm.embed(new EmbedRequest(model.id(),
                    batch.stream().map(CodeChunkDraft::text).toList()));
            if (result.vectors().size() != batch.size()) {
                // The gateway checks the width; the count is checked here, because a provider that drops a text
                // would silently shift every later vector onto the wrong chunk.
                throw new IllegalStateException(model.label() + " returned " + result.vectors().size()
                        + " vector(s) for " + batch.size() + " chunk(s)");
            }
            for (int index = 0; index < batch.size(); index++) {
                embedded.add(new CodeChunkStore.EmbeddedChunk(runId, batch.get(index), embedModel,
                        result.vectors().get(index)));
            }
            tokens.add(result.usage());
        }

        int storedCount = store.replaceAll(runId, embedModel, embedded);
        log.info("indexed {} chunk(s) of run {} with {} in {} embedding call(s)", storedCount, runId, embedModel,
                tokens.calls());
        return new IndexedRun(drafts, new EmbeddingIndex(runId, model, embedModel, storedCount, false,
                tokens.usage(), skippedSymbols(runId)));
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
