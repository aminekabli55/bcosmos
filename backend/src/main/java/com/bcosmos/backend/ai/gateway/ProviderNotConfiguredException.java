package com.bcosmos.backend.ai.gateway;

/** A provider has no endpoint, so there is nowhere to send the call. */
public class ProviderNotConfiguredException extends LlmException {

    public ProviderNotConfiguredException(String providerCode, String what) {
        super("provider-not-configured",
                "provider " + providerCode + " has no " + what + ": set it in the provider registry", false);
    }
}
