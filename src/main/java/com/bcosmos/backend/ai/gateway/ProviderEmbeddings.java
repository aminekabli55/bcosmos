package com.bcosmos.backend.ai.gateway;

import java.util.List;

/**
 * What an adapter read out of a provider embedding response.
 *
 * @param vectors  one vector per input text, in order
 * @param tokensIn input tokens, or 0 when the provider does not report embedding usage
 */
public record ProviderEmbeddings(List<float[]> vectors, int tokensIn) {

    public ProviderEmbeddings {
        vectors = List.copyOf(vectors);
    }
}
