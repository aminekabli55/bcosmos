package com.bcosmos.backend.ai.gateway;

/** No provider row has this id. */
public class ProviderNotFoundException extends LlmException {

    public ProviderNotFoundException(long providerId) {
        super("provider-not-found", "no provider is registered with id " + providerId, false);
    }
}
