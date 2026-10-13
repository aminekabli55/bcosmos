package com.bcosmos.backend.ai.gateway;

import java.util.Objects;

/**
 * What an adapter read out of a provider response.
 *
 * @param text      the model's text
 * @param tokensIn  input tokens as the provider reported them, 0 when it reported none
 * @param tokensOut output tokens as the provider reported them, 0 when it reported none
 */
public record ProviderCompletion(String text, int tokensIn, int tokensOut) {

    public ProviderCompletion {
        Objects.requireNonNull(text, "text");
    }
}
