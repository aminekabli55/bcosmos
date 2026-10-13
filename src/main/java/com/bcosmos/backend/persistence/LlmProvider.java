package com.bcosmos.backend.persistence;

/**
 * Hosted providers the gateway knows how to talk to (spec v2 section 7.1).
 *
 * <p>{@link #OPENAI_COMPATIBLE} is deliberately one value rather than a vendor list: many providers expose the
 * same HTTP shape, and pointing {@code bcosmos.ai.providers.openai-compatible.base-url} at their endpoint is the
 * whole configuration.</p>
 */
public enum LlmProvider {

    /** Google Gemini: {@code generativelanguage.googleapis.com}. */
    GEMINI,

    /** Any endpoint that speaks the OpenAI chat-completions and embeddings shape. */
    OPENAI_COMPATIBLE
}
