package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProvider;
import java.util.List;

/**
 * The provider side of the gateway: one implementation per HTTP shape (spec v2 section 7.1).
 *
 * <p>An adapter knows a provider's endpoint, auth header, request body and response shape, and nothing else. Model
 * selection, opt-in, redaction, retries, timeouts, schema validation and usage recording happen in
 * {@link LlmGateway}, so two providers cannot behave differently by accident.</p>
 */
public interface LlmProviderAdapter {

    /** Which provider this adapter serves. */
    LlmProvider provider();

    /** One completion. Transport and credential failures must be raised as {@link LlmException}s. */
    ProviderCompletion complete(ModelRef model, CompletionRequest request);

    /** One embedding call for the given texts, in order. */
    ProviderEmbeddings embed(ModelRef model, List<String> texts);
}
