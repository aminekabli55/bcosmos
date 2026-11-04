package com.bcosmos.backend.ai.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts a credential the user pasted, so the settings screen can store one without putting plaintext in the
 * database (ADR 0038, spec v2 section 10 "environment variables or a secrets store, never committed").
 *
 * <p>AES-256-GCM with a random 96-bit IV per encryption, and the provider code as associated data — so a
 * ciphertext moved to another provider row fails to decrypt instead of quietly authenticating the wrong endpoint.
 * The master key is 32 raw bytes in base64 from {@code BCOSMOS_AI_SECRET_KEY}: it comes from the environment, is
 * never stored, logged or returned, and is not derivable from anything in the database.</p>
 *
 * <p>What this does <em>not</em> do: it does not protect against someone who can read both the database and the
 * process environment. It protects against the realistic accident — a dump, a backup or a log file carrying a
 * working key.</p>
 */
public final class SecretCipher {

    /** Size of the master key, in bytes. */
    public static final int KEY_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** {@code null} when no master key is configured: storing a key is then refused, reading one is an error. */
    private final SecretKey masterKey;

    private SecretCipher(SecretKey masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * Reads the master key from its base64 form.
     *
     * @throws IllegalStateException when the value is present but is not the base64 of exactly
     *                               {@value #KEY_BYTES} bytes — a wrong key must fail at startup, not at the first
     *                               call, and never be silently ignored
     */
    public static SecretCipher fromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new SecretCipher(null);
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException("bcosmos.ai.secret-key must be the base64 of " + KEY_BYTES
                    + " random bytes (openssl rand -base64 " + KEY_BYTES + "); the configured value is not base64");
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("bcosmos.ai.secret-key must decode to exactly " + KEY_BYTES
                    + " bytes (openssl rand -base64 " + KEY_BYTES + "); the configured value decoded to "
                    + raw.length + " bytes");
        }
        return new SecretCipher(new SecretKeySpec(raw, ALGORITHM));
    }

    /** Whether a master key is configured, so a credential can be stored and read. */
    public boolean available() {
        return masterKey != null;
    }

    /**
     * Encrypts one credential.
     *
     * @param associatedData binds the ciphertext to its row; the provider code
     * @return base64 of {@code IV || ciphertext || tag}
     * @throws SecretStorageUnavailableException when no master key is configured
     */
    public String encrypt(String plaintext, String associatedData) {
        if (masterKey == null) {
            throw SecretStorageUnavailableException.notConfigured();
        }
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(sealed, 0, payload, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("AES-GCM encryption is unavailable: " + failure.getMessage(), failure);
        }
    }

    /**
     * Decrypts one stored credential.
     *
     * @throws StoredCredentialUnreadableException when the master key changed or the ciphertext was altered —
     *                                             never an empty string, which would be sent to a provider as a
     *                                             silent authentication failure
     */
    public String decrypt(String ciphertext, String associatedData) {
        if (masterKey == null) {
            throw StoredCredentialUnreadableException.masterKeyMissing();
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException notBase64) {
            throw StoredCredentialUnreadableException.malformed();
        }
        if (payload.length <= IV_BYTES) {
            throw StoredCredentialUnreadableException.malformed();
        }
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(payload, 0, iv, 0, IV_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failure) {
            throw StoredCredentialUnreadableException.wrongMasterKey();
        }
    }

    private static byte[] aad(String associatedData) {
        return (associatedData == null ? "" : associatedData).getBytes(StandardCharsets.UTF_8);
    }
}
