package com.bcosmos.backend.ai.gateway;

/** This provider already has a registry row with that model name; the pair is unique. */
public class DuplicateModelException extends LlmException {

    public DuplicateModelException(String providerCode, String modelName) {
        super("duplicate-model", "provider " + providerCode + " already has a model named '" + modelName + "'", false);
    }
}
