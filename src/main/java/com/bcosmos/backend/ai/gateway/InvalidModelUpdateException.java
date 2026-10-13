package com.bcosmos.backend.ai.gateway;

/** A registry edit that cannot be applied as asked, with the reason a user can act on. */
public class InvalidModelUpdateException extends LlmException {

    public InvalidModelUpdateException(String message) {
        super("invalid-model-update", message, false);
    }
}
