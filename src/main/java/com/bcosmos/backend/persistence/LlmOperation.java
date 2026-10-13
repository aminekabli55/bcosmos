package com.bcosmos.backend.persistence;

/** The three things the gateway can ask a model to do (spec v2 section 7.1). */
public enum LlmOperation {

    /** Free text. */
    CHAT,

    /** Validated JSON against a schema. */
    STRUCTURED,

    /** Embedding vectors. */
    EMBED
}
