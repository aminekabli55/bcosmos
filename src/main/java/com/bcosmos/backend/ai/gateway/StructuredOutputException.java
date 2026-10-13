package com.bcosmos.backend.ai.gateway;

import java.util.List;

/**
 * The model never produced JSON the schema accepted, not even after the retry (spec v2 section 7.1).
 *
 * <p>The validation messages describe the shape of the answer ("{@code $.summary: required property missing}"), not
 * the code that was sent, so they are safe to return and to log.</p>
 */
public class StructuredOutputException extends LlmException {

    private final List<String> validationErrors;

    public StructuredOutputException(String modelLabel, List<String> validationErrors) {
        super(LlmErrorCode.SCHEMA_INVALID,
                "the answer from " + modelLabel + " did not match the schema after a retry: "
                        + String.join("; ", validationErrors),
                false);
        this.validationErrors = List.copyOf(validationErrors);
    }

    /** What was wrong with the last answer. */
    public List<String> validationErrors() {
        return validationErrors;
    }
}
