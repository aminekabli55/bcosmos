package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link LlmProviderStore} on top of Spring Data. Entities stay inside this class. */
@Repository
public class JpaLlmProviderStore implements LlmProviderStore {

    private final LlmProviderJpaRepository providers;

    public JpaLlmProviderStore(LlmProviderJpaRepository providers) {
        this.providers = providers;
    }

    @Override
    public List<LlmProviderRecord> findAll() {
        return providers.findAllByOrderByCodeAsc().stream().map(JpaLlmProviderStore::toRecord).toList();
    }

    @Override
    public Optional<LlmProviderRecord> findById(long id) {
        return providers.findById(id).map(JpaLlmProviderStore::toRecord);
    }

    @Override
    public Optional<LlmProviderRecord> findByCode(String code) {
        return providers.findByCode(code).map(JpaLlmProviderStore::toRecord);
    }

    /**
     * Inserts or applies a provider.
     *
     * <p>An existing row receives the whole editable state, not a patch: the registry loads the row, applies the
     * edit to the record and saves the result, so "leave it" has already been resolved by the time we get here. An
     * empty environment-variable name means the reference is removed, and {@code null} ciphertext and hint mean the
     * stored key is cleared.</p>
     */
    @Override
    public LlmProviderRecord save(LlmProviderRecord provider) {
        if (provider.id() == null) {
            return toRecord(providers.save(new LlmProviderEntity(
                    provider.code(),
                    provider.label(),
                    provider.protocol(),
                    provider.baseUrl(),
                    provider.apiKeyEnv(),
                    provider.apiKeyCiphertext(),
                    provider.apiKeyHint(),
                    provider.apiKeyRequired(),
                    provider.createdAt())));
        }
        LlmProviderEntity entity = providers.findById(provider.id())
                .orElseThrow(() -> new IllegalStateException("provider " + provider.id() + " disappeared"));
        entity.edit(provider.label(), provider.protocol(), provider.baseUrl(),
                provider.apiKeyEnv() == null ? "" : provider.apiKeyEnv(), provider.apiKeyRequired(),
                provider.updatedAt());
        entity.storeKey(provider.apiKeyCiphertext(), provider.apiKeyHint(), provider.updatedAt());
        return toRecord(providers.save(entity));
    }

    @Override
    public void delete(long id) {
        providers.deleteById(id);
    }

    /** Shared with {@link JpaLlmModelStore}, which hands its models their provider row. */
    static LlmProviderRecord toRecord(LlmProviderEntity entity) {
        return new LlmProviderRecord(
                entity.getId(),
                entity.getCode(),
                entity.getLabel(),
                entity.getProtocol(),
                entity.getBaseUrl(),
                entity.getApiKeyEnv(),
                entity.getApiKeyCiphertext(),
                entity.getApiKeyHint(),
                entity.isApiKeyRequired(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
