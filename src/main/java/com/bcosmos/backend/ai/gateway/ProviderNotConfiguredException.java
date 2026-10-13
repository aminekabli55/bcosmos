package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProvider;

/** A provider has no endpoint configured, so there is nowhere to send the call. */
public class ProviderNotConfiguredException extends LlmException {

    public ProviderNotConfiguredException(LlmProvider provider, String property) {
        super("provider-not-configured", "provider " + provider + " has no endpoint: set " + property, false);
    }
}
