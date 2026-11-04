package com.bcosmos.backend.ai.gateway;

import java.util.Objects;

/**
 * A model's answer, with the model that gave it and what it cost (spec v2 section 7.1: usage recording).
 *
 * <p>The model travels with the answer so a caller can show or store which model said what without resolving the
 * registry again.</p>
 *
 * @param model the registry model that answered
 * @param text  the model's text, as the provider returned it
 * @param usage tokens, latency, attempts and estimated cost
 */
public record ChatResult(ModelRef model, String text, LlmUsage usage) {

    public ChatResult {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(usage, "usage");
    }
}
