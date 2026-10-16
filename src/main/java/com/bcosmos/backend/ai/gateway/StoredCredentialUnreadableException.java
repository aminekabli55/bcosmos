package com.bcosmos.backend.ai.gateway;

/**
 * A credential is stored for this provider but cannot be decrypted.
 *
 * <p>Almost always the master key changed. It is raised rather than swallowed because the alternative — treating
 * an unreadable key as "no key" — turns a configuration mistake into an authentication failure at the provider,
 * which is both harder to diagnose and a paid round trip.</p>
 */
public class StoredCredentialUnreadableException extends LlmException {

    public StoredCredentialUnreadableException(String message) {
        super("stored-credential-unreadable", message, false);
    }

    static StoredCredentialUnreadableException masterKeyMissing() {
        return new StoredCredentialUnreadableException(
                "this provider has an encrypted API key but no master key is configured: set "
                        + "BCOSMOS_AI_SECRET_KEY to the value that encrypted it, or store the key again");
    }

    static StoredCredentialUnreadableException wrongMasterKey() {
        return new StoredCredentialUnreadableException(
                "the stored API key cannot be decrypted with the current BCOSMOS_AI_SECRET_KEY; store the key "
                        + "again in Settings (the old value is unreadable, not missing)");
    }

    static StoredCredentialUnreadableException malformed() {
        return new StoredCredentialUnreadableException(
                "the stored API key is not in the expected encrypted form; store the key again in Settings");
    }
}
