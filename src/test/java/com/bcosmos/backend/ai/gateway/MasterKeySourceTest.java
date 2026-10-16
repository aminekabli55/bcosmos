package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the master key comes from (ADR 0038, ADR 0039): the configured value wins, a key file is read, and only a
 * profile that asks for it generates one — the difference between a deployment and a development machine.
 */
@DisplayName("Master key resolution")
class MasterKeySourceTest {

    private static final String CONFIGURED = Base64.getEncoder()
            .encodeToString("configured-master-key-0123456789".getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path directory;

    /** A path two levels deep, so generation has a directory to create. */
    private Path keyFile() {
        return directory.resolve("nested").resolve("ai-secret.key");
    }

    @Test
    void usesTheConfiguredKeyAndLeavesNoFileBehind() {
        SecretCipher cipher = MasterKeySource.resolve(CONFIGURED, keyFile(), true);

        assertThat(cipher.available()).isTrue();
        assertThat(Files.exists(keyFile())).isFalse();
    }

    @Test
    void readsAKeyFileThatIsAlreadyThere() throws Exception {
        String stored = Base64.getEncoder()
                .encodeToString("file-master-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        givenKeyFile(stored);

        SecretCipher cipher = MasterKeySource.resolve("", keyFile(), true);

        assertThat(cipher.available()).isTrue();
        // The file is read as written: a value put there by hand must work, not be silently replaced.
        assertThat(Files.readString(keyFile()).trim()).isEqualTo(stored);
    }

    @Test
    void generatesAKeyOnFirstStartAndReusesItOnEveryLaterStart() {
        SecretCipher first = MasterKeySource.resolve("", keyFile(), true);
        SecretCipher second = MasterKeySource.resolve("", keyFile(), true);

        assertThat(first.available()).isTrue();
        // The same bytes, or every credential encrypted on the first start would be unreadable on the second.
        String ciphertext = first.encrypt("sk-test-key", "gemini");
        assertThat(second.decrypt(ciphertext, "gemini")).isEqualTo("sk-test-key");
    }

    @Test
    void writesThirtyTwoRandomBytesAsBase64() throws Exception {
        MasterKeySource.resolve("", keyFile(), true);

        String written = Files.readString(keyFile()).trim();
        assertThat(Base64.getDecoder().decode(written)).hasSize(SecretCipher.KEY_BYTES);
    }

    @Test
    void createsTheDirectorySoTheDefaultLocationNeedsNoSetup() {
        assertThat(Files.exists(keyFile().getParent())).isFalse();

        MasterKeySource.resolve("", keyFile(), true);

        assertThat(Files.isRegularFile(keyFile())).isTrue();
    }

    @Test
    void doesNotGenerateAnythingWhenTheProfileDoesNotAllowIt() {
        // The production shape: no key in the environment, no file, generation off — so pasting a key is refused.
        SecretCipher cipher = MasterKeySource.resolve("", keyFile(), false);

        assertThat(cipher.available()).isFalse();
        assertThat(Files.exists(keyFile())).isFalse();
    }

    @Test
    void isNotConfiguredWhenNoFileIsConfiguredEither() {
        assertThat(MasterKeySource.resolve("", null, false).available()).isFalse();
        // Generation needs somewhere to write: with no path, asking for it changes nothing.
        assertThat(MasterKeySource.resolve("", null, true).available()).isFalse();
    }

    @Test
    void fillsAnEmptyKeyFileRatherThanUsingNoKeyAtAll() throws Exception {
        givenKeyFile("\n");

        SecretCipher cipher = MasterKeySource.resolve("", keyFile(), true);

        assertThat(cipher.available()).isTrue();
        assertThat(Files.readString(keyFile()).trim()).isNotBlank();
    }

    @Test
    void failsLoudlyOnAKeyFileThatIsNotAKey() throws Exception {
        givenKeyFile("this is not base64");

        assertThatThrownBy(() -> MasterKeySource.resolve("", keyFile(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not base64");
    }

    private void givenKeyFile(String content) throws Exception {
        Files.createDirectories(keyFile().getParent());
        Files.writeString(keyFile(), content);
    }
}
