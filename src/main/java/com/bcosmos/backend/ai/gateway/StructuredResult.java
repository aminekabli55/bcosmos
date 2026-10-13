package com.bcosmos.backend.ai.gateway;

import java.util.List;
import java.util.Objects;

/**
 * A validated structured answer.
 *
 * @param model            the registry model that answered
 * @param value            the bound object
 * @param usage            tokens, latency, attempts and estimated cost, summed over both attempts when the first
 *                         answer was rejected
 * @param validationErrors what the first answer got wrong, when it had to be retried; empty on a first-time pass
 * @param <T>              the bound type
 */
public record StructuredResult<T>(ModelRef model, T value, LlmUsage usage, List<String> validationErrors) {

    public StructuredResult {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(usage, "usage");
        validationErrors = List.copyOf(validationErrors);
    }

    /** Whether a schema rejection forced a second attempt. */
    public boolean retried() {
        return !validationErrors.isEmpty();
    }
}
