package com.bcosmos.backend.ai.gateway;

/** The model exists but is not enabled, so sending code to its provider would not be opt-in (spec section 10). */
public class ModelDisabledException extends LlmException {

    public ModelDisabledException(String modelName) {
        super(LlmErrorCode.MODEL_DISABLED,
                "model '" + modelName + "' is registered but not enabled; enable it in settings first", false);
    }
}
