package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.persistence.ProviderProtocol;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Adding a vendor has to be a data change, and the credential has to survive it without leaking. */
class ProviderRegistryTest {

    private static final String ENV_KEY = "env-deepseek-key-000000";
    private static final String PASTED_KEY = "sk-pasted-deepseek-0123456789";

    private final AiTestSupport.FakeProviderStore providers = new AiTestSupport.FakeProviderStore();
    private final AiTestSupport.FakeModelStore models = new AiTestSupport.FakeModelStore();
    private final SecretCipher cipher = AiTestSupport.cipher();
    private final ProviderCredentials credentials = new ProviderCredentials(providers, cipher,
            HostEnvironment.of(Map.of("DEEPSEEK_API_KEY", ENV_KEY)));
    private final ProviderRegistry registry = new ProviderRegistry(providers, models, credentials, cipher,
            Clock.systemUTC());

    @Test
    void registersAVendorAndReportsOnlyAHintOfItsKey() {
        ProviderListing created = registry.create(new ProviderDraft("DeepSeek", "DeepSeek",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "DEEPSEEK_API_KEY", null,
                PASTED_KEY));

        assertThat(created.code()).isEqualTo("deepseek");
        assertThat(created.credentialSource()).isEqualTo(CredentialSource.STORED);
        assertThat(created.credentialHint()).isEqualTo("6789");
        assertThat(created.credentialEnvironmentVariable()).isEqualTo("DEEPSEEK_API_KEY");
        assertThat(created.toString()).doesNotContain(PASTED_KEY);
    }

    @Test
    void storesThePastedKeyEncryptedAndResolvesItForACall() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1", PASTED_KEY));

        String stored = providers.findById(created.id()).orElseThrow().apiKeyCiphertext();
        assertThat(stored).isNotNull().doesNotContain(PASTED_KEY);
        // The row wins over the environment: a key the user pasted is the one that is used.
        assertThat(credentials.resolve(registry.require(created.id()))).isEqualTo(PASTED_KEY);
    }

    @Test
    void fallsBackToTheEnvironmentVariableTheRowNames() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1", null));

        assertThat(created.credentialSource()).isEqualTo(CredentialSource.ENVIRONMENT);
        assertThat(created.credentialHint()).isNull();
        assertThat(credentials.resolve(registry.require(created.id()))).isEqualTo(ENV_KEY);
    }

    @Test
    void treatsAProviderThatTakesNoKeyAsUsableWithNone() {
        ProviderListing created = registry.create(new ProviderDraft("ollama", "Ollama", ProviderProtocol.OPENAI_COMPATIBLE,
                "http://localhost:11434/v1", null, false, null));

        assertThat(created.credentialSource()).isEqualTo(CredentialSource.NOT_REQUIRED);
        // No key at all, and not a failure either: the adapter leaves the authorization header off.
        assertThat(credentials.resolve(registry.require(created.id()))).isNull();
    }

    @Test
    void refusesToStoreAKeyWithoutAMasterKey() {
        ProviderRegistry withoutCipher = new ProviderRegistry(providers, models, credentials,
                SecretCipher.fromBase64(""), Clock.systemUTC());

        assertThatThrownBy(() -> withoutCipher.create(draft("https://api.deepseek.com/v1", PASTED_KEY)))
                .isInstanceOf(SecretStorageUnavailableException.class);
    }

    @Test
    void refusesTwoProvidersWithTheSameCode() {
        registry.create(draft("https://api.deepseek.com/v1", null));

        assertThatThrownBy(() -> registry.create(draft("https://api.deepseek.com/v1", null)))
                .isInstanceOf(DuplicateProviderException.class)
                .hasMessageContaining("deepseek");
    }

    @Test
    void refusesTheShapeOfRowsThatWouldBreakACallLater() {
        assertThatThrownBy(() -> registry.create(new ProviderDraft("Deep Seek!", "label",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", null, null, null)))
                .isInstanceOf(InvalidProviderUpdateException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> registry.create(new ProviderDraft("deepseek", " ",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", null, null, null)))
                .isInstanceOf(InvalidProviderUpdateException.class)
                .hasMessageContaining("label");
        assertThatThrownBy(() -> registry.create(new ProviderDraft("deepseek", "DeepSeek", null,
                "https://api.deepseek.com/v1", null, null, null)))
                .isInstanceOf(InvalidProviderUpdateException.class)
                .hasMessageContaining("protocol");
        assertThatThrownBy(() -> registry.create(new ProviderDraft("deepseek", "DeepSeek",
                ProviderProtocol.OPENAI_COMPATIBLE, "api.deepseek.com", null, null, null)))
                .isInstanceOf(InvalidProviderUpdateException.class)
                .hasMessageContaining("http");
        assertThatThrownBy(() -> registry.create(new ProviderDraft("deepseek", "DeepSeek",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "not a variable", null, null)))
                .isInstanceOf(InvalidProviderUpdateException.class)
                .hasMessageContaining("environment variable");
    }

    @Test
    void normalizesTheEndpointSoNoPathIsAppendedOntoADoubleSlash() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1///", null));

        assertThat(created.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
    }

    @Test
    void editsTheEndpointWithoutTouchingTheStoredKey() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1", PASTED_KEY));

        ProviderListing edited = registry.edit(created.id(), new ProviderEdit("DeepSeek (platform)", null,
                "https://api.deepseek.com/beta", null, null, null));

        assertThat(edited.label()).isEqualTo("DeepSeek (platform)");
        assertThat(edited.baseUrl()).isEqualTo("https://api.deepseek.com/beta");
        assertThat(edited.credentialSource()).isEqualTo(CredentialSource.STORED);
        assertThat(credentials.resolve(registry.require(created.id()))).isEqualTo(PASTED_KEY);
    }

    @Test
    void clearsAStoredKeyWithAnEmptyString() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1", PASTED_KEY));

        ProviderListing cleared = registry.edit(created.id(), new ProviderEdit(null, null, null, null, null, ""));

        assertThat(cleared.credentialSource()).isEqualTo(CredentialSource.ENVIRONMENT);
        assertThat(cleared.credentialHint()).isNull();
    }

    @Test
    void refusesToRemoveAProviderThatStillHasModels() {
        ProviderListing created = registry.create(new ProviderDraft("openai", "OpenAI",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "OPENAI_API_KEY", null, null));
        models.save(AiTestSupport.newModel(providers.findById(created.id()).orElseThrow(), "gpt-4o-mini", false,
                false));

        assertThatThrownBy(() -> registry.delete(created.id()))
                .isInstanceOf(ProviderInUseException.class)
                .hasMessageContaining("1 model");
        assertThat(registry.listing()).hasSize(1);
    }

    @Test
    void removesAProviderWithoutModels() {
        ProviderListing created = registry.create(draft("https://api.deepseek.com/v1", null));

        registry.delete(created.id());

        assertThat(registry.listing()).isEmpty();
        assertThatThrownBy(() -> registry.require(created.id())).isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void countsTheModelsOfAProviderSoTheScreenCanSayWhyItCannotBeRemoved() {
        ProviderListing created = registry.create(new ProviderDraft("openai", "OpenAI",
                ProviderProtocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "OPENAI_API_KEY", null, null));
        var provider = providers.findById(created.id()).orElseThrow();
        models.save(AiTestSupport.newModel(provider, "gpt-4o-mini", false, false));
        models.save(AiTestSupport.newModel(provider, "gpt-4o", false, false));

        assertThat(registry.listing()).singleElement()
                .satisfies(listing -> assertThat(listing.modelCount()).isEqualTo(2));
    }

    private static ProviderDraft draft(String baseUrl, String apiKey) {
        return new ProviderDraft("deepseek", "DeepSeek", ProviderProtocol.OPENAI_COMPATIBLE, baseUrl,
                "DEEPSEEK_API_KEY", null, apiKey);
    }
}
