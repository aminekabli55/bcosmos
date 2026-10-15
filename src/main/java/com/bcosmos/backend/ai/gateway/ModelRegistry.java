package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelRecord;
import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may be called, and with which model (spec v2 sections 7.1 and 8: registry, {@code GET /api/models},
 * {@code PUT /api/models/{id}}).
 *
 * <p>Two gates stand between a finding and a provider: the registry row must be {@code enabled} (the per-model
 * opt-in of spec section 10) and its provider must have a credential in the environment. Enabling a model without a
 * key is refused here rather than failing later inside a call, so the settings screen can say why.</p>
 */
public class ModelRegistry {

    private final LlmModelStore store;
    private final ProviderCredentials credentials;
    private final Clock clock;

    public ModelRegistry(LlmModelStore store, ProviderCredentials credentials, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Every registry row, as the settings screen lists them. */
    @Transactional(readOnly = true)
    public List<ModelListing> listing() {
        return store.findAll().stream().map(this::toListing).toList();
    }

    /** Every registry row as a callable model, without the settings-only fields. */
    @Transactional(readOnly = true)
    public List<ModelRef> all() {
        return store.findAll().stream().map(ModelRegistry::toRef).toList();
    }

    /** One row by id, whether or not it is enabled. */
    @Transactional(readOnly = true)
    public ModelRef require(long modelId) {
        return toRef(entity(modelId));
    }

    /**
     * The chat model a call should use: the named one, or the registry's default chat model.
     *
     * @param modelId registry id, or {@code null} to take the default
     * @throws ModelDisabledException   when the row exists but is not enabled
     * @throws ModelCapabilityException when the named row is an embedding model
     */
    @Transactional(readOnly = true)
    public ModelRef requireUsable(Long modelId) {
        LlmModelRecord model = modelId == null ? defaultChatEntity() : entity(modelId);
        requireChatCapability(model);
        if (!model.enabled()) {
            throw new ModelDisabledException(model.provider() + "/" + model.modelName());
        }
        return toRef(model);
    }

    /**
     * The embedding model an index build should use: the named one, or the registry's default embedding model.
     *
     * <p>When no embedding row is marked as default, the first enabled one is used, so a fresh database works as
     * soon as a single embedding model is switched on. Nothing enabled at all is an error the caller can act on,
     * not an empty index.</p>
     */
    @Transactional(readOnly = true)
    public ModelRef requireEmbeddingModel(Long modelId) {
        LlmModelRecord model = modelId == null ? defaultEmbeddingEntity() : entity(modelId);
        if (!model.embeddingCapable()) {
            throw new ModelCapabilityException(model.provider() + "/" + model.modelName() + " is a "
                    + model.capability() + " model; embeddings need an EMBEDDING model");
        }
        if (!model.enabled()) {
            throw new ModelDisabledException(model.provider() + "/" + model.modelName());
        }
        return toRef(model);
    }

    /** The chat row the API uses when a request does not name a model. */
    @Transactional(readOnly = true)
    public ModelRef defaultModel() {
        return toRef(defaultChatEntity());
    }

    /** Whether the provider of this model has a credential in the current environment. */
    public boolean hasCredential(ModelRef model) {
        return credentials.isConfigured(model.provider());
    }

    /** The provider of a model, for messages that should not repeat the whole registry row. */
    public static LlmProvider providerOf(ModelRef model) {
        return model.provider();
    }

    /**
     * Applies an edit from the settings screen.
     *
     * <p>Making a model the default clears the previous one in the same transaction: "at most one default" is an
     * invariant of this class, not a convention callers have to remember.</p>
     */
    @Transactional
    public ModelListing edit(long modelId, ModelEdit edit) {
        LlmModelRecord model = entity(modelId);
        boolean becomesDefault = Boolean.TRUE.equals(edit.defaultModel());
        if (Boolean.TRUE.equals(edit.enabled()) && !credentials.isConfigured(model.provider())) {
            throw new MissingApiKeyException(model.provider(), credentials.environmentVariable(model.provider()));
        }
        if (Boolean.FALSE.equals(edit.defaultModel()) && model.defaultModel()) {
            throw new InvalidModelUpdateException("model '" + model.modelName()
                    + "' is the default " + model.capability().name().toLowerCase(java.util.Locale.ROOT)
                    + " model; make another model the default first");
        }
        if (edit.contextWindow() != null && edit.contextWindow() <= 0) {
            throw new InvalidModelUpdateException("contextWindow must be positive");
        }
        if (isNegative(edit.costPer1kIn()) || isNegative(edit.costPer1kOut())) {
            throw new InvalidModelUpdateException("prices cannot be negative");
        }
        if (becomesDefault) {
            // Defaults are per capability: promoting an embedding model must not clear the chat default.
            store.findDefault(model.capability())
                    .filter(previous -> !previous.id().equals(modelId))
                    .ifPresent(previous -> store.save(previous.withEdit(null, false, null, null, null, null,
                            clock.instant())));
        }
        return toListing(store.save(model.withEdit(edit.enabled(), edit.defaultModel(), edit.contextWindow(),
                edit.costPer1kIn(), edit.costPer1kOut(), edit.notes(), clock.instant())));
    }

    private LlmModelRecord entity(long modelId) {
        return store.findById(modelId).orElseThrow(() -> new ModelNotFoundException(modelId));
    }

    private LlmModelRecord defaultChatEntity() {
        return store.findDefault(LlmModelCapability.CHAT).orElseThrow(() -> new ModelNotConfiguredException(
                "no chat model is marked as the default; choose one in settings"));
    }

    private LlmModelRecord defaultEmbeddingEntity() {
        List<LlmModelRecord> embeddingModels = store.findByCapability(LlmModelCapability.EMBEDDING);
        return embeddingModels.stream().filter(LlmModelRecord::defaultModel).findFirst()
                .or(() -> embeddingModels.stream().filter(LlmModelRecord::enabled).findFirst())
                .orElseThrow(() -> new ModelNotConfiguredException(
                        "no embedding model is enabled; enable one in settings before indexing code"));
    }

    /** Chat and structured calls must not be pointed at an embedding model. */
    private static void requireChatCapability(LlmModelRecord model) {
        if (model.embeddingCapable()) {
            throw new ModelCapabilityException(model.provider() + "/" + model.modelName()
                    + " is an EMBEDDING model; it cannot answer chat or structured calls");
        }
    }

    private static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private ModelListing toListing(LlmModelRecord model) {
        LlmProvider provider = model.provider();
        return new ModelListing(
                model.id(),
                provider,
                model.modelName(),
                model.kind(),
                model.capability(),
                model.embeddingDimension(),
                model.enabled(),
                model.defaultModel(),
                model.contextWindow(),
                model.costPer1kIn(),
                model.costPer1kOut(),
                model.notes(),
                model.updatedAt(),
                credentials.isConfigured(provider),
                credentials.environmentVariable(provider));
    }

    /** The registry row as the gateway's value type. */
    public static ModelRef toRef(LlmModelRecord model) {
        return new ModelRef(
                model.id(),
                model.provider(),
                model.modelName(),
                model.kind(),
                model.capability(),
                model.embeddingDimension(),
                model.contextWindow(),
                model.costPer1kIn(),
                model.costPer1kOut());
    }
}
