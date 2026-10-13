package com.bcosmos.backend.ai.gateway;

/** No registry row has this id. */
public class ModelNotFoundException extends LlmException {

    public ModelNotFoundException(long modelId) {
        super("model-not-found", "no model is registered with id " + modelId, false);
    }
}
