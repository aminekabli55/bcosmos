package com.bcosmos.backend.ai.gateway;

/**
 * A provider is still referenced by registry rows, so removing it would leave models without an endpoint.
 *
 * <p>The foreign key would refuse the delete anyway; this exists so the answer is a message naming the count instead
 * of a constraint violation. Delete or move the models first.</p>
 */
public class ProviderInUseException extends LlmException {

    public ProviderInUseException(String providerCode, long modelCount) {
        super("provider-in-use", "provider " + providerCode + " still has " + modelCount + " model"
                + (modelCount == 1 ? "" : "s") + " in the registry; move or delete them first", false);
    }
}
