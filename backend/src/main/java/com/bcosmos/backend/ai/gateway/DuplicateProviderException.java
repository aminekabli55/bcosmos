package com.bcosmos.backend.ai.gateway;

/** A provider with this code already exists; the code is the stable identity and the unique key. */
public class DuplicateProviderException extends LlmException {

    public DuplicateProviderException(String code) {
        super("duplicate-provider", "a provider with code '" + code + "' is already registered", false);
    }
}
