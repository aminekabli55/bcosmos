package com.bcosmos.backend.ai.gateway;

/**
 * The request named a model that cannot do what was asked: a chat call pointed at an embedding model, or an
 * embedding call pointed at a chat model.
 *
 * <p>It fails here, before any request is sent, because the provider's answer to that mistake is expensive at best
 * and silently wrong at worst.</p>
 */
public class ModelCapabilityException extends LlmException {

    public ModelCapabilityException(String message) {
        super("model-capability", message, false);
    }
}
