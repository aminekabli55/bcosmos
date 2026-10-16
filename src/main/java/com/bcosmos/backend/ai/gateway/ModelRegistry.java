package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmModelCapability;
import com.bcosmos.backend.persistence.LlmModelKind;
import com.bcosmos.backend.persistence.LlmModelRecord;
import com.bcosmos.backend.persistence.LlmModelStore;
import com.bcosmos.backend.persistence.LlmProviderRecord;
import com.bcosmos.backend.persistence.LlmProviderStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may be called, and with which model (spec v2 sections 7.1 and 8: registry, {@code GET /api/models},
 * {@code PUT /api/models/{id}}).
 *
 * <p>Two gates stand between a finding and a provider: the registry row must be {@code enabled} (the per-model
 * opt-in of spec section 10) and its provider row must have a usable credential. Enabling a model without one is
 * refused here rather than failing later inside a call, so the settings screen can say why.</p>
 *
 * <p>Since Phase 11 a row can also be added and removed at runtime: the provider — endpoint, protocol, credential —
 * is a row, so supporting a new vendor, or a new model of a known vendor, does not need a deployment (ADR
 * 0038).</p>
 */
public class ModelRegistry {

    private static final int DEFAULT_CONTEXT_WINDOW = 128_000;
    private static final int MAX_MODEL_NAME = 120;
    private static final int MAX_NOTES = 500;

    private final LlmModelStore store;
    private final LlmProviderStore providers;
    private final ProviderCredentials credentials;
    private final Clock clock;

    public ModelRegistry(LlmModelStore store, LlmProviderStore providers, ProviderCredentials credentials,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.providers = Objects.requireNonNull(providers, "providers");
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
            throw new ModelDisabledException(model.provider().code() + "/" + model.modelName());
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
            throw new ModelCapabilityException(model.provider().code() + "/" + model.modelName() + " is a "
                    + model.capability() + " model; embeddings need an EMBEDDING model");
        }
        if (!model.enabled()) {
            throw new ModelDisabledException(model.provider().code() + "/" + model.modelName());
        }
        return toRef(model);
    }

    /** The chat row the API uses when a request does not name a model. */
    @Transactional(readOnly = true)
    public ModelRef defaultModel() {
        return toRef(defaultChatEntity());
    }

    /** Whether the provider of this model has a credential right now. */
    public boolean hasCredential(ModelRef model) {
        return credentials.isConfigured(model.provider());
    }

    /** The provider of a model, for messages that should not repeat the whole registry row. */
    public static ProviderRef providerOf(ModelRef model) {
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
        if (Boolean.TRUE.equals(edit.enabled()) && !credentials.isConfigured(toRef(model).provider())) {
            throw new MissingApiKeyException(model.provider().code(), model.provider().apiKeyEnv());
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

    /**
     * Registers a model of an existing provider.
     *
     * <p>Adding a vendor is a provider row; adding a model of that vendor is this call. The row starts disabled
     * unless the caller says otherwise, because sending source code to a new endpoint is the same opt-in decision as
     * enabling any other model (spec v2 section 10).</p>
     */
    @Transactional
    public ModelListing create(ModelDraft draft) {
        LlmProviderRecord provider = providers.findById(draft.providerId())
                .orElseThrow(() -> new ProviderNotFoundException(draft.providerId()));
        String modelName = requireModelName(draft.modelName());
        if (store.existsByProviderAndModelName(provider.id(), modelName)) {
            throw new DuplicateModelException(provider.code(), modelName);
        }
        LlmModelKind kind = draft.kind() == null ? LlmModelKind.HOSTED : draft.kind();
        LlmModelCapability capability = draft.capability() == null ? LlmModelCapability.CHAT : draft.capability();
        Integer dimension = requireDimension(capability, draft.embeddingDimension());
        int contextWindow = draft.contextWindow() == null || draft.contextWindow() <= 0
                ? DEFAULT_CONTEXT_WINDOW
                : draft.contextWindow();
        BigDecimal costIn = draft.costPer1kIn() == null ? BigDecimal.ZERO : draft.costPer1kIn();
        BigDecimal costOut = draft.costPer1kOut() == null ? BigDecimal.ZERO : draft.costPer1kOut();
        if (isNegative(costIn) || isNegative(costOut)) {
            throw new InvalidModelUpdateException("prices cannot be negative");
        }
        boolean enabled = Boolean.TRUE.equals(draft.enabled());
        ProviderRef ref = ProviderRef.of(provider);
        if (enabled && !credentials.isConfigured(ref)) {
            throw new MissingApiKeyException(provider.code(), provider.apiKeyEnv());
        }
        if (draft.notes() != null && draft.notes().length() > MAX_NOTES) {
            throw new InvalidModelUpdateException("notes are longer than " + MAX_NOTES + " characters");
        }
        boolean becomesDefault = Boolean.TRUE.equals(draft.defaultModel());
        if (becomesDefault) {
            store.findDefault(capability)
                    .ifPresent(previous -> store.save(previous.withEdit(null, false, null, null, null, null,
                            clock.instant())));
        }
        Instant now = clock.instant();
        LlmModelRecord row = new LlmModelRecord(null, provider, modelName, kind, capability, dimension, enabled,
                becomesDefault, contextWindow, costIn, costOut, draft.notes(), now, now);
        return toListing(store.save(row));
    }

    /**
     * Removes a registry row.
     *
     * <p>The default row cannot go first, because the API needs a default chat model to answer a request that names
     * none. History is safe either way: usage rows and suggestions keep their own copy of the model name and their
     * foreign keys are {@code ON DELETE SET NULL}.</p>
     */
    @Transactional
    public void delete(long modelId) {
        LlmModelRecord model = entity(modelId);
        if (model.defaultModel()) {
            throw new InvalidModelUpdateException("model '" + model.modelName() + "' is the default "
                    + model.capability().name().toLowerCase(java.util.Locale.ROOT)
                    + " model; make another model the default before deleting it");
        }
        store.delete(modelId);
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
            throw new ModelCapabilityException(model.provider().code() + "/" + model.modelName()
                    + " is an EMBEDDING model; it cannot answer chat or structured calls");
        }
    }

    private static String requireModelName(String modelName) {
        if (isBlank(modelName)) {
            throw new InvalidModelUpdateException("modelName is required, exactly as the provider spells it");
        }
        String trimmed = modelName.trim();
        if (trimmed.length() > MAX_MODEL_NAME) {
            throw new InvalidModelUpdateException("modelName is longer than " + MAX_MODEL_NAME + " characters");
        }
        return trimmed;
    }

    /** An embedding row must declare its width: it is what the vector column and the HNSW index are built on. */
    private static Integer requireDimension(LlmModelCapability capability, Integer dimension) {
        if (capability != LlmModelCapability.EMBEDDING) {
            return null;
        }
        if (dimension == null || dimension <= 0) {
            throw new InvalidModelUpdateException("an EMBEDDING model needs embeddingDimension, the vector width "
                    + "the provider returns (the code_chunk column is built for "
                    + "bcosmos.retrieval.embedding-dimension)");
        }
        return dimension;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private ModelListing toListing(LlmModelRecord model) {
        ProviderRef provider = ProviderRef.of(model.provider());
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
                credentials.source(provider),
                model.provider().apiKeyHint(),
                model.provider().apiKeyEnv());
    }

    /** The registry row as the gateway's value type. */
    public static ModelRef toRef(LlmModelRecord model) {
        return new ModelRef(
                model.id(),
                ProviderRef.of(model.provider()),
                model.modelName(),
                model.kind(),
                model.capability(),
                model.embeddingDimension(),
                model.contextWindow(),
                model.costPer1kIn(),
                model.costPer1kOut());
    }
}
