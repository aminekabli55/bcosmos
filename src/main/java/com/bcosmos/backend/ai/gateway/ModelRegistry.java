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
 *
 * <p>Which model a call uses is always named by the caller. There is no stored default: a flag that a browser has
 * to interpret (two rows marked "default", one of them for embeddings) made a choice look implicit, and a call that
 * names nothing now fails with the list of models that could have been named (ADR 0042).</p>
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
     * The chat model a call should use.
     *
     * @param modelId registry id the caller named
     * @throws ModelNotConfiguredException when the call named no model; the message lists the ones to choose from
     * @throws ModelDisabledException      when the row exists but is not enabled
     * @throws ModelCapabilityException    when the named row is an embedding model
     */
    @Transactional(readOnly = true)
    public ModelRef requireUsable(Long modelId) {
        LlmModelRecord model = modelId == null ? undefined(LlmModelCapability.CHAT) : entity(modelId);
        requireChatCapability(model);
        if (!model.enabled()) {
            throw new ModelDisabledException(model.provider().code() + "/" + model.modelName());
        }
        return toRef(model);
    }

    /**
     * The embedding model an index build should use.
     *
     * @param modelId registry id the caller named
     * @throws ModelNotConfiguredException when the call named no model; the message lists the ones to choose from
     */
    @Transactional(readOnly = true)
    public ModelRef requireEmbeddingModel(Long modelId) {
        LlmModelRecord model = modelId == null ? undefined(LlmModelCapability.EMBEDDING) : entity(modelId);
        if (!model.embeddingCapable()) {
            throw new ModelCapabilityException(model.provider().code() + "/" + model.modelName() + " is a "
                    + model.capability() + " model; embeddings need an EMBEDDING model");
        }
        if (!model.enabled()) {
            throw new ModelDisabledException(model.provider().code() + "/" + model.modelName());
        }
        return toRef(model);
    }

    /** Whether the provider of this model has a credential right now. */
    public boolean hasCredential(ModelRef model) {
        return credentials.isConfigured(model.provider());
    }

    /** The provider of a model, for messages that should not repeat the whole registry row. */
    public static ProviderRef providerOf(ModelRef model) {
        return model.provider();
    }

    /** Applies an edit from the settings screen. */
    @Transactional
    public ModelListing edit(long modelId, ModelEdit edit) {
        LlmModelRecord model = entity(modelId);
        if (Boolean.TRUE.equals(edit.enabled()) && !credentials.isConfigured(toRef(model).provider())) {
            throw new MissingApiKeyException(model.provider().code(), model.provider().apiKeyEnv());
        }
        if (edit.contextWindow() != null && edit.contextWindow() <= 0) {
            throw new InvalidModelUpdateException("contextWindow must be positive");
        }
        if (isNegative(edit.costPer1kIn()) || isNegative(edit.costPer1kOut())) {
            throw new InvalidModelUpdateException("prices cannot be negative");
        }
        return toListing(store.save(model.withEdit(edit.enabled(), edit.contextWindow(),
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
        Instant now = clock.instant();
        LlmModelRecord row = new LlmModelRecord(null, provider, modelName, kind, capability, dimension, enabled,
                contextWindow, costIn, costOut, draft.notes(), now, now);
        return toListing(store.save(row));
    }

    /**
     * Removes a registry row.
     *
     * <p>Nothing has to be demoted first: no call can be pointing at this row implicitly, because every call names
     * its model. History is safe either way — usage rows and suggestions keep their own copy of the model name and
     * their foreign keys are {@code ON DELETE SET NULL}.</p>
     */
    @Transactional
    public void delete(long modelId) {
        store.delete(entity(modelId).id());
    }

    private LlmModelRecord entity(long modelId) {
        return store.findById(modelId).orElseThrow(() -> new ModelNotFoundException(modelId));
    }

    /**
     * Never returns: the failure for a call that named no model, saying what it should have named.
     *
     * <p>It lists the enabled rows of the capability, because "name a chat model" is only actionable if the caller
     * can see which ones exist, and it names the settings screen when there are none at all.</p>
     */
    private LlmModelRecord undefined(LlmModelCapability capability) {
        String required = capability == LlmModelCapability.EMBEDDING ? "embedding" : "chat";
        List<String> callable = store.findByCapability(capability).stream()
                .filter(LlmModelRecord::enabled)
                .map(model -> model.provider().code() + "/" + model.modelName() + " (id " + model.id() + ")")
                .toList();
        if (callable.isEmpty()) {
            throw new ModelNotConfiguredException("this call has to name a " + required + " model, and no "
                    + required + " model is enabled; enable one in settings first");
        }
        throw new ModelNotConfiguredException("this call has to name the model it wants: there is no default any"
                + " more, so name a " + required + " model — " + String.join(", ", callable));
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
