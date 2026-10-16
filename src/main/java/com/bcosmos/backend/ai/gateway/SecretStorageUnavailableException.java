package com.bcosmos.backend.ai.gateway;

/**
 * A credential was to be stored, but there is no master key to encrypt it with.
 *
 * <p>Refusing is the point: silently writing the key in the clear would turn "encrypted at rest" into a promise the
 * code does not keep. The message names the variable to set (ADR 0038).</p>
 */
public class SecretStorageUnavailableException extends LlmException {

    public SecretStorageUnavailableException(String message) {
        super("secret-storage-unavailable", message, false);
    }

    /** The common case: no master key in the environment. */
    public static SecretStorageUnavailableException notConfigured() {
        return new SecretStorageUnavailableException(
                "no master key is configured, so an API key cannot be stored: set BCOSMOS_AI_SECRET_KEY to the "
                        + "base64 of 32 random bytes (openssl rand -base64 32) and restart. Until then a provider "
                        + "reads its key from the environment variable its row names.");
    }
}
