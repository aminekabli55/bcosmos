package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link LlmModelStore} on top of Spring Data. Entities stay inside this class. */
@Repository
public class JpaLlmModelStore implements LlmModelStore {

    private final LlmModelJpaRepository models;
    private final LlmProviderJpaRepository providers;

    public JpaLlmModelStore(LlmModelJpaRepository models, LlmProviderJpaRepository providers) {
        this.models = models;
        this.providers = providers;
    }

    @Override
    public List<LlmModelRecord> findAll() {
        return models.findAllByOrderByProviderIdAscModelNameAsc().stream().map(JpaLlmModelStore::toRecord).toList();
    }

    @Override
    public Optional<LlmModelRecord> findById(long id) {
        return models.findById(id).map(JpaLlmModelStore::toRecord);
    }

    @Override
    public List<LlmModelRecord> findByCapability(LlmModelCapability capability) {
        return models.findByCapabilityOrderByProviderIdAscModelNameAsc(capability).stream()
                .map(JpaLlmModelStore::toRecord)
                .toList();
    }

    @Override
    public LlmModelRecord save(LlmModelRecord model) {
        if (model.id() == null) {
            // A reference, not a fetch: the provider row must exist (the foreign key says so), and there is no
            // reason to read it back to insert a model that points at it.
            LlmProviderEntity provider = providers.getReferenceById(model.provider().id());
            return toRecord(models.save(new LlmModelEntity(
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
                    model.createdAt())));
        }
        LlmModelEntity entity = models.findById(model.id())
                .orElseThrow(() -> new IllegalStateException("registry row " + model.id() + " disappeared"));
        entity.edit(model.enabled(), model.contextWindow(), model.costPer1kIn(),
                model.costPer1kOut(), model.notes(), model.updatedAt());
        return toRecord(models.save(entity));
    }

    @Override
    public long countByProvider(long providerId) {
        return models.countByProviderId(providerId);
    }

    @Override
    public boolean existsByProviderAndModelName(long providerId, String modelName) {
        return models.existsByProviderIdAndModelName(providerId, modelName);
    }

    @Override
    public void delete(long id) {
        models.deleteById(id);
    }

    private static LlmModelRecord toRecord(LlmModelEntity entity) {
        return new LlmModelRecord(
                entity.getId(),
                JpaLlmProviderStore.toRecord(entity.getProvider()),
                entity.getModelName(),
                entity.getKind(),
                entity.getCapability(),
                entity.getEmbeddingDimension(),
                entity.isEnabled(),
                entity.getContextWindow(),
                entity.getCostPer1kIn(),
                entity.getCostPer1kOut(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
