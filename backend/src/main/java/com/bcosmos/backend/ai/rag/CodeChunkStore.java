package com.bcosmos.backend.ai.rag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores and searches embedded chunks (spec v2 sections 4.1 and 7.2, table {@code code_chunk}).
 *
 * <p>It is a port rather than a Spring Data repository because the interesting column is a pgvector
 * {@code vector} with an HNSW index: the SQL that writes and searches it is the point, not something to hide
 * behind an entity mapping. Retrieval and the similarity query are therefore testable against a real Postgres.</p>
 */
public interface CodeChunkStore {

    /**
     * Replaces every chunk of one run for one embedding model.
     *
     * <p>Replacing rather than appending is what makes indexing idempotent: running it twice leaves the same rows,
     * and switching embedding model leaves the old model's rows untouched but unused (a vector is only comparable
     * with vectors from the same model and width).</p>
     *
     * @return how many chunks are stored afterwards
     */
    int replaceAll(long runId, String embedModel, List<EmbeddedChunk> chunks);

    /**
     * Writes the chunks of one finished batch, leaving the rest of the run's index in place (ADR 0044).
     *
     * <p>This is what makes a build resumable: every batch that the provider answered is durable immediately, so a
     * rate limit after it costs only the batch that failed, not the whole index. The rows are written under the same
     * conflict key as {@link #replaceAll}, so re-embedding a chunk updates it in place.</p>
     *
     * @return how many rows were written
     */
    int upsertAll(long runId, String embedModel, List<EmbeddedChunk> chunks);

    /**
     * The text of every stored chunk of one run and model, keyed by symbol id.
     *
     * <p>A build compares it with the chunk plan: a chunk whose stored text is unchanged needs no call, and one
     * whose text differs was produced by another chunking limit and must be embedded again.</p>
     */
    Map<Long, String> storedTexts(long runId, String embedModel);

    /**
     * Removes stored chunks whose symbol is no longer part of the run's chunk plan.
     *
     * <p>Without it a stale row would both survive in retrieval and make a partial index look complete, because
     * completeness is "as many rows as the plan has chunks".</p>
     *
     * @return how many rows were removed
     */
    int prune(long runId, String embedModel, List<Long> keepSymbolIds);

    /** How many chunks one run has for one embedding model. */
    long count(long runId, String embedModel);

    /** The {@code limit} chunks of a run closest to the query vector, nearest first. */
    List<StoredChunk> nearest(long runId, String embedModel, float[] query, int limit);

    /** A chunk with its vector, ready to be written. */
    record EmbeddedChunk(long runId, CodeChunkDraft draft, String embedModel, float[] embedding) {

        public EmbeddedChunk {
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(embedModel, "embedModel");
            Objects.requireNonNull(embedding, "embedding");
        }
    }

    /** A stored chunk as retrieval reads it back. */
    record StoredChunk(
            long symbolId,
            long fileId,
            String relativePath,
            String qualifiedName,
            int startLine,
            int endLine,
            String text,
            int tokenCount,
            String embedModel,
            double distance) {

        public StoredChunk {
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(qualifiedName, "qualifiedName");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(embedModel, "embedModel");
        }

        /** Cosine similarity in {@code [0, 1]} for the ranges pgvector returns, for display only. */
        public double similarity() {
            return Math.max(0.0, Math.min(1.0, 1.0 - distance));
        }
    }
}
