package com.bcosmos.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much code may be gathered around one finding, and how it is chunked (spec v2 section 7.2: "retrieval for a
 * finding, bounded to a token budget").
 *
 * <p>Every limit is configuration rather than a constant, because the budget interacts with the chosen model's
 * context window and with how much a free tier is willing to accept. The defaults are deliberately small: a
 * suggestion prompt that does not fit is a provider error the user pays for.</p>
 *
 * @param embeddingDimension width of every stored vector; must equal the {@code code_chunk.embedding} column width
 *                           and the {@code embedding_dimension} of the model in use, or the embedding is refused
 * @param topK               how many semantically similar chunks retrieval adds on top of the enclosing symbol
 * @param tokenBudget        estimated tokens of code one suggestion prompt may carry
 * @param maxChunkChars      cap on one chunk's text; a longer symbol is truncated with a visible marker
 * @param embedBatchSize     texts per embedding call, so one huge repository does not ask for one huge request
 * @param indexTextsPerCall  how many not-yet-embedded texts one index build may send, so a suggestion on a large
 *                           repository answers in bounded time and the rest of the index follows with the next
 *                           request (ADR 0044). Zero or less means "embed the whole run in one call", which is
 *                           only sensible on a provider without a per-minute quota
 * @param rateLimitPatience  how long one index build may sit out rate limits in total; a provider asking for longer
 *                           than this fails the build with the wait in the message, because a spent daily quota
 *                           cannot be waited out. Zero turns the waiting off (ADR 0041)
 */
@ConfigurationProperties(prefix = "bcosmos.retrieval")
public record RetrievalProperties(
        int embeddingDimension,
        int topK,
        int tokenBudget,
        int maxChunkChars,
        int embedBatchSize,
        int indexTextsPerCall,
        Duration rateLimitPatience) {

    private static final int DEFAULT_DIMENSION = 1536;
    private static final int DEFAULT_TOP_K = 8;
    private static final int DEFAULT_TOKEN_BUDGET = 6000;
    private static final int DEFAULT_MAX_CHUNK_CHARS = 6000;
    private static final int DEFAULT_EMBED_BATCH_SIZE = 16;
    private static final int DEFAULT_INDEX_TEXTS_PER_CALL = 96;
    private static final Duration DEFAULT_RATE_LIMIT_PATIENCE = Duration.ofMinutes(2);

    public RetrievalProperties {
        embeddingDimension = embeddingDimension <= 0 ? DEFAULT_DIMENSION : embeddingDimension;
        topK = topK <= 0 ? DEFAULT_TOP_K : topK;
        tokenBudget = tokenBudget <= 0 ? DEFAULT_TOKEN_BUDGET : tokenBudget;
        maxChunkChars = maxChunkChars <= 0 ? DEFAULT_MAX_CHUNK_CHARS : maxChunkChars;
        embedBatchSize = embedBatchSize <= 0 ? DEFAULT_EMBED_BATCH_SIZE : embedBatchSize;
        // A missing value gets the default; zero and negative values are kept on purpose, as the way a deployment
        // asks for one large build instead of a bounded one.
        indexTextsPerCall = indexTextsPerCall == 0 ? DEFAULT_INDEX_TEXTS_PER_CALL : indexTextsPerCall;
        // Zero is passed through on purpose: it is how a deployment says "never sit out a rate limit", while a
        // missing or nonsensical value falls back to the default.
        rateLimitPatience = rateLimitPatience == null ? DEFAULT_RATE_LIMIT_PATIENCE : rateLimitPatience;
    }
}
