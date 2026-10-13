package com.bcosmos.backend.ai.gateway;

/** A call named no model and the registry has no default, so there is nothing to ask. */
public class ModelNotConfiguredException extends LlmException {

    public ModelNotConfiguredException(String message) {
        super("model-not-configured", message, false);
    }
}
