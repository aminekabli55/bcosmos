package com.bcosmos.backend.ai.gateway;

import static com.bcosmos.backend.ai.gateway.AiTestSupport.embeddingModel;
import static com.bcosmos.backend.ai.gateway.AiTestSupport.model;
import static com.bcosmos.backend.ai.gateway.AiTestSupport.properties;
import static com.bcosmos.backend.ai.gateway.AiTestSupport.propertiesWithoutKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The registry is the only gate between a finding and a provider, so every rule is checked here. */
class ModelRegistryTest {

    private final AiTestSupport.FakeModelStore store = new AiTestSupport.FakeModelStore(
            model(1, LlmProvider.GEMINI, "gemini-2.5-flash", true, true),
            model(2, LlmProvider.OPENAI_COMPATIBLE, "gpt-4o-mini", false, false),
            model(3, LlmProvider.GEMINI, "gemini-2.5-pro", false, false),
            embeddingModel(4, LlmProvider.GEMINI, "gemini-embedding-2", true, true, 1536),
            embeddingModel(5, LlmProvider.OPENAI_COMPATIBLE, "text-embedding-3-small", false, false, 1536));

    private final ProviderCredentials credentials = new ProviderCredentials(
            properties(true, "http://localhost:1", "http://localhost:1", Duration.ofSeconds(5), 2));

    private final ModelRegistry registry = new ModelRegistry(store, credentials, Clock.systemUTC());

    @Test
    void resolvesTheDefaultModelWhenACallNamesNone() {
        assertThat(registry.requireUsable(null).modelName()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void refusesAModelThatIsRegisteredButNotEnabled() {
        assertThatThrownBy(() -> registry.requireUsable(2L))
                .isInstanceOf(ModelDisabledException.class)
                .hasMessageContaining("gpt-4o-mini")
                .hasMessageContaining("not enabled");
    }

    @Test
    void refusesAnUnknownModelId() {
        assertThatThrownBy(() -> registry.require(99L))
                .isInstanceOf(ModelNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void refusesToEnableAModelWhoseProviderHasNoKey() {
        ModelRegistry keyless = new ModelRegistry(store, new ProviderCredentials(propertiesWithoutKeys(true)),
                Clock.systemUTC());

        assertThatThrownBy(() -> keyless.edit(2, new ModelEdit(true, null, null, null, null, null)))
                .isInstanceOf(MissingApiKeyException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .hasMessageContaining("OPENAI_COMPATIBLE");
    }

    @Test
    void reportsWhichProvidersHaveACredential() {
        ModelListing withKey = registry.listing().stream().filter(row -> row.id() == 1L).findFirst().orElseThrow();

        assertThat(withKey.credentialConfigured()).isTrue();
        assertThat(withKey.credentialEnvironmentVariable()).isEqualTo("GEMINI_API_KEY");
    }

    @Test
    void makingAModelTheDefaultClearsThePreviousOne() {
        ModelListing promoted = registry.edit(2, new ModelEdit(true, true, null, null, null, null));

        assertThat(promoted.defaultModel()).isTrue();
        assertThat(store.defaults(LlmModelCapability.CHAT)).extracting("modelName").containsExactly("gpt-4o-mini");
        assertThat(registry.requireUsable(null).modelName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void refusesToLeaveTheRegistryWithoutADefault() {
        assertThatThrownBy(() -> registry.edit(1, new ModelEdit(null, false, null, null, null, null)))
                .isInstanceOf(InvalidModelUpdateException.class)
                .hasMessageContaining("make another model the default first");
    }

    @Test
    void refusesEditsThatWouldProduceNonsenseRows() {
        assertThatThrownBy(() -> registry.edit(1, new ModelEdit(null, null, 0, null, null, null)))
                .isInstanceOf(InvalidModelUpdateException.class)
                .hasMessageContaining("contextWindow");
        assertThatThrownBy(() -> registry.edit(1, new ModelEdit(null, null, null, new BigDecimal("-1"), null, null)))
                .isInstanceOf(InvalidModelUpdateException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void recordsTheNotesAndPricesAnEditSends() {
        registry.edit(3, new ModelEdit(null, null, 2_000_000, new BigDecimal("0.000100"), new BigDecimal("0.000400"),
                "cheaper long-context model"));

        ModelListing edited = registry.listing().stream().filter(row -> row.id() == 3L).findFirst().orElseThrow();
        assertThat(edited.contextWindow()).isEqualTo(2_000_000);
        assertThat(edited.costPer1kIn()).isEqualByComparingTo("0.000100");
        assertThat(edited.notes()).isEqualTo("cheaper long-context model");
        assertThat(edited.updatedAt()).isAfter(AiTestSupport.CREATED);
    }

    @Test
    void refusesToChatWithAnEmbeddingModelAndToEmbedWithAChatModel() {
        assertThatThrownBy(() -> registry.requireUsable(4L))
                .isInstanceOf(ModelCapabilityException.class)
                .hasMessageContaining("EMBEDDING model")
                .hasMessageContaining("gemini-embedding-2");
        assertThatThrownBy(() -> registry.requireEmbeddingModel(1L))
                .isInstanceOf(ModelCapabilityException.class)
                .hasMessageContaining("is a CHAT model");
    }

    @Test
    void resolvesTheDefaultEmbeddingModelWithoutTouchingTheChatDefault() {
        assertThat(registry.requireEmbeddingModel(null).modelName()).isEqualTo("gemini-embedding-2");
        assertThat(registry.requireEmbeddingModel(null).embeddingDimension()).isEqualTo(1536);
        assertThat(registry.requireUsable(null).modelName()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void refusesAnEmbeddingModelThatIsNotEnabled() {
        assertThatThrownBy(() -> registry.requireEmbeddingModel(5L))
                .isInstanceOf(ModelDisabledException.class)
                .hasMessageContaining("text-embedding-3-small");
    }

    @Test
    void promotingAnEmbeddingModelLeavesTheChatDefaultAlone() {
        ModelListing promoted = registry.edit(5, new ModelEdit(true, true, null, null, null, null));

        assertThat(promoted.defaultModel()).isTrue();
        assertThat(registry.requireUsable(null).modelName()).isEqualTo("gemini-2.5-flash");
        assertThat(registry.requireEmbeddingModel(null).modelName()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void resolvesTheProviderOfAModel() {
        assertThat(ModelRegistry.providerOf(registry.require(2))).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(registry.hasCredential(registry.require(2))).isTrue();
        assertThat(registry.all()).hasSize(5);
    }
}
