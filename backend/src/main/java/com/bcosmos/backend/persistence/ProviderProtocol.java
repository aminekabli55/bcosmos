package com.bcosmos.backend.persistence;

/**
 * The wire shape a provider speaks, which is what selects an adapter (spec v2 section 7.1).
 *
 * <p>It is deliberately not a vendor list. {@link #OPENAI_COMPATIBLE} covers every vendor that exposes the OpenAI
 * chat-completions and embeddings contract — DeepSeek, Groq, OpenRouter, xAI, Mistral, Together, Ollama, vLLM and
 * Anthropic's own compatibility layer — so supporting one of them is a row in {@code llm_provider}, not a class.
 * A vendor whose protocol is genuinely different gets a new constant and one adapter next to it.</p>
 */
public enum ProviderProtocol {

    /** Google Gemini: {@code /v1beta/models/{model}:generateContent}, key in the {@code x-goog-api-key} header. */
    GEMINI,

    /**
     * Any endpoint that speaks {@code /chat/completions} and {@code /embeddings} under the configured base URL,
     * with the key in {@code Authorization: Bearer}.
     */
    OPENAI_COMPATIBLE
}
