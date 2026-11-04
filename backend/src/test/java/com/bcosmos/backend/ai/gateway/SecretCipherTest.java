package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Stored credentials are encrypted, bound to their provider and never readable without the master key. */
class SecretCipherTest {

    private static final String KEY = "sk-deepseek-0123456789abcdef";
    private static final String CODE = "deepseek";

    private final SecretCipher cipher = AiTestSupport.cipher();

    @Test
    void roundTripsAKeyAndKeepsThePlaintextOutOfTheStoredForm() {
        String stored = cipher.encrypt(KEY, CODE);

        assertThat(stored).doesNotContain(KEY);
        assertThat(cipher.decrypt(stored, CODE)).isEqualTo(KEY);
    }

    @Test
    void producesADifferentCiphertextEveryTime() {
        // A random IV per encryption: identical keys must not look identical in the table.
        assertThat(cipher.encrypt(KEY, CODE)).isNotEqualTo(cipher.encrypt(KEY, CODE));
    }

    @Test
    void bindsTheCiphertextToItsProvider() {
        String stored = cipher.encrypt(KEY, CODE);

        // Moving a ciphertext to another provider row must fail instead of authenticating the wrong endpoint.
        assertThatThrownBy(() -> cipher.decrypt(stored, "openai"))
                .isInstanceOf(StoredCredentialUnreadableException.class)
                .hasMessageContaining("store the key again");
    }

    @Test
    void refusesToStoreWhenNoMasterKeyIsConfigured() {
        SecretCipher withoutKey = SecretCipher.fromBase64("");

        assertThat(withoutKey.available()).isFalse();
        assertThatThrownBy(() -> withoutKey.encrypt(KEY, CODE))
                .isInstanceOf(SecretStorageUnavailableException.class)
                .hasMessageContaining("BCOSMOS_AI_SECRET_KEY")
                .hasMessageContaining("openssl rand -base64 32");
    }

    @Test
    void refusesToReadAStoredKeyWhenTheMasterKeyChanged() {
        String stored = cipher.encrypt(KEY, CODE);
        SecretCipher otherKey = SecretCipher.fromBase64(
                Base64.getEncoder().encodeToString("another-32-byte-master-key-00000".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> otherKey.decrypt(stored, CODE))
                .isInstanceOf(StoredCredentialUnreadableException.class);
    }

    @Test
    void refusesTamperedCiphertextInsteadOfReturningSomething() {
        String stored = cipher.encrypt(KEY, CODE);
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> cipher.decrypt(tampered, CODE))
                .isInstanceOf(StoredCredentialUnreadableException.class);
    }

    @Test
    void failsAtStartupWhenTheConfiguredMasterKeyIsNotUsable() {
        // A wrong key must fail the deployment, not the first call that needed to decrypt something.
        assertThatThrownBy(() -> SecretCipher.fromBase64("not-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not base64");
        assertThatThrownBy(() -> SecretCipher.fromBase64(Base64.getEncoder().encodeToString("too short".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decoded to 9 bytes");
    }
}
