package com.bcosmos.backend.ai.gateway;

/** The whole AI layer is switched off ({@code bcosmos.ai.enabled=false}); nothing was sent anywhere. */
public class LlmDisabledException extends LlmException {

    public LlmDisabledException() {
        super(LlmErrorCode.DISABLED,
                "the AI layer is switched off; enable it in settings before asking a model", false);
    }
}
