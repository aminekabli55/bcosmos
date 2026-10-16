package com.bcosmos.backend.ai.gateway;

import static com.bcosmos.backend.ai.gateway.AiTestSupport.embeddingModel;
import static com.bcosmos.backend.ai.gateway.AiTestSupport.model;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmProviderRecord;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The registry is the only gate between a finding and a provider, so every rule is checked here. */
class ModelRegistryTest {

    private static final String NO_WHERE = "http://localhost:1";

    private static final LlmProviderRecord GEMINI = AiTestSupport.gemini(NO_WHERE);
    private static final LlmProviderRecord OPENAI = AiTestSupport.openai(NO_WHERE);

    private final AiTestSupport.FakeModelStore store = new AiTestSupport.FakeModelStore(
            model(1, GEMINI, "gemini-2.5-flash", true, true),
            model(2, OPENAI, "gpt-4o-mini", false, false),
            model(3, GEMINI, "gemini-2.5-pro", false, false),
            embeddingModel(4, GEMINI, "gemini-embedding-2", true, true, 1536),
            embeddingModel(5, OPENAI, "text-embedding-3-small", false, false, 1536));

    private final AiTestSupport.FakeProviderStore providerStore = new AiTestSupport.FakeProviderStore(GEMINI, OPENAI);

    private final ProviderCredentials credentials = AiTestSupport.credentials(GEMINI, OPENAI);

    private final ModelRegistry registry = new ModelRegistry(store, providerStore, credentials, Clock.systemUTC());

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
        // No environment and no stored key: the provider row names OPENAI_API_KEY, which is not set here.
        ProviderCredentials keyless = AiTestSupport.credentials(Map.of(), GEMINI, OPENAI);
        ModelRegistry registryWithoutKeys = new ModelRegistry(store, providerStore, keyless, Clock.systemUTC());

        assertThatThrownBy(() -> registryWithoutKeys.edit(2, new ModelEdit(true, null, null, null, null, null)))
                .isInstanceOf(MissingApiKeyException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .hasMessageContaining("openai");
    }

    @Test
    void reportsWhereEachProviderCredentialComesFrom() {
        ModelListing fromEnvironment = registry.listing().stream().filter(row -> row.id() == 1L).findFirst()
                .orElseThrow();

        assertThat(fromEnvironment.credentialSource()).isEqualTo(CredentialSource.ENVIRONMENT);
        assertThat(fromEnvironment.credentialEnvironmentVariable()).isEqualTo(AiTestSupport.GEMINI_ENV);
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
        assertThat(ModelRegistry.providerOf(registry.require(2)).code()).isEqualTo("openai");
        assertThat(registry.hasCredential(registry.require(2))).isTrue();
        assertThat(registry.all()).hasSize(5);
    }

    // --- registering and removing models at runtime (Phase 11) -------------------------------------------------

    @Test
    void registersAModelOfAnExistingProviderWithSensibleDefaults() {
        ModelListing created = registry.create(draft("deepseek-chat"));

        assertThat(created.provider().code()).isEqualTo("openai");
        assertThat(created.modelName()).isEqualTo("deepseek-chat");
        assertThat(created.kind()).isEqualTo(LlmModelKind.HOSTED);
        assertThat(created.capability()).isEqualTo(LlmModelCapability.CHAT);
        assertThat(created.contextWindow()).isEqualTo(128_000);
        // A model added through the screen is a fresh opt-in decision, so it starts switched off.
        assertThat(created.enabled()).isFalse();
        assertThat(registry.listing()).hasSize(6);
    }

    @Test
    void refusesTwoModelsOfTheSameNameOnOneProvider() {
        assertThatThrownBy(() -> registry.create(draft("gpt-4o-mini")))
                .isInstanceOf(DuplicateModelException.class)
                .hasMessageContaining("gpt-4o-mini");
    }

    @Test
    void refusesAModelOnAProviderThatDoesNotExist() {
        assertThatThrownBy(() -> registry.create(new ModelDraft(99L, "whatever", null, null, null, false, null,
                null, null, null, null)))
                .isInstanceOf(ProviderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void refusesToRegisterAnEnabledModelWhenItsProviderHasNoKey() {
        ModelRegistry registryWithoutKeys = new ModelRegistry(store, providerStore,
                AiTestSupport.credentials(Map.of(), GEMINI, OPENAI), Clock.systemUTC());

        assertThatThrownBy(() -> registryWithoutKeys.create(new ModelDraft(2L, "gpt-4o", null, null, null, true,
                null, null, null, null, null)))
                .isInstanceOf(MissingApiKeyException.class)
                .hasMessageContaining(AiTestSupport.OPENAI_ENV);
    }

    @Test
    void refusesAnEmbeddingModelWithoutADeclaredWidth() {
        assertThatThrownBy(() -> registry.create(new ModelDraft(2L, "text-embedding-3-large", null,
                LlmModelCapability.EMBEDDING, null, false, null, null, null, null, null)))
                .isInstanceOf(InvalidModelUpdateException.class)
                .hasMessageContaining("embeddingDimension");
    }

    @Test
    void registeringADefaultClearsThePreviousOne() {
        ModelListing created = registry.create(new ModelDraft(1L, "gemini-3-pro", null, null, null, true, true, null,
                null, null, null));

        assertThat(created.defaultModel()).isTrue();
        assertThat(store.defaults(LlmModelCapability.CHAT)).extracting("modelName").containsExactly("gemini-3-pro");
        assertThat(registry.requireUsable(null).modelName()).isEqualTo("gemini-3-pro");
    }

    @Test
    void removesAModelThatIsNotTheDefault() {
        registry.delete(3L);

        assertThat(registry.listing()).hasSize(4);
        assertThatThrownBy(() -> registry.require(3L)).isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void refusesToRemoveTheDefaultModel() {
        assertThatThrownBy(() -> registry.delete(1L))
                .isInstanceOf(InvalidModelUpdateException.class)
                .hasMessageContaining("make another model the default before deleting it");
    }

    @Test
    void refusesToRemoveAModelThatIsNotThere() {
        assertThatThrownBy(() -> registry.delete(99L)).isInstanceOf(ModelNotFoundException.class);
    }

    private static ModelDraft draft(String modelName) {
        return new ModelDraft(2L, modelName, null, null, null, false, null, null, null, null, null);
    }
}
