package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.ProviderProtocol;
import java.util.List;

/**
 * The provider side of the gateway: one implementation per <em>wire shape</em>, not per vendor (spec v2 section
 * 7.1).
 *
 * <p>An adapter knows a protocol's paths, auth header, request body and response shape, and nothing else. Which
 * vendor, which endpoint and which credential is a provider row it is handed per call, so adding DeepSeek, Groq,
 * Ollama or OpenRouter is a database change rather than a new class (ADR 0038). Model selection, opt-in, redaction,
 * retries, timeouts, schema validation and usage recording happen in {@link LlmGateway}, so two protocols cannot
 * behave differently by accident.</p>
 */
public interface LlmProviderAdapter {

    /** Which wire shape this adapter serves. */
    ProviderProtocol protocol();

    /** One completion. Transport and credential failures must be raised as {@link LlmException}s. */
    ProviderCompletion complete(ModelRef model, CompletionRequest request);

    /** One embedding call for the given texts, in order. */
    ProviderEmbeddings embed(ModelRef model, List<String> texts);
}
