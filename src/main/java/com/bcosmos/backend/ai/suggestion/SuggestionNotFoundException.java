package com.bcosmos.backend.ai.suggestion;

/** No stored suggestion has this id. */
public class SuggestionNotFoundException extends RuntimeException {

    public SuggestionNotFoundException(long suggestionId) {
        super("suggestion " + suggestionId + " does not exist");
    }
}
