package com.bcosmos.backend.ai.gateway;

/** A provider create or edit that cannot be applied as asked, with the reason a user can act on. */
public class InvalidProviderUpdateException extends LlmException {

    public InvalidProviderUpdateException(String message) {
        super("invalid-provider-update", message, false);
    }
}
