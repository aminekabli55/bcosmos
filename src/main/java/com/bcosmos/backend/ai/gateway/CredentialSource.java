package com.bcosmos.backend.ai.gateway;

/**
 * Where a provider's credential comes from, as the settings screen needs to report it.
 *
 * <p>The distinction is not cosmetic: {@code STORED} means the key was pasted into the screen and is encrypted in
 * the database, {@code ENVIRONMENT} means the row points at a variable and the value is in the process environment,
 * and {@code MISSING} is why a model cannot be enabled. The value itself is never part of any of these (ADR
 * 0038).</p>
 */
public enum CredentialSource {

    /** Encrypted in {@code llm_provider}, decrypted only for the length of a request. */
    STORED,

    /** Read from the variable named by the provider row. */
    ENVIRONMENT,

    /** The endpoint takes no credential (a local Ollama or vLLM server). */
    NOT_REQUIRED,

    /** A credential is required and neither source has one. */
    MISSING
}
