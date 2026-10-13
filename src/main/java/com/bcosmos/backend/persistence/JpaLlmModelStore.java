package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link LlmModelStore} on top of Spring Data. Entities stay inside this class. */
@Repository
public class JpaLlmModelStore implements LlmModelStore {

    private final LlmModelJpaRepository models;

    public JpaLlmModelStore(LlmModelJpaRepository models) {
        this.models = models;
    }

    @Override
    public List<LlmModelRecord> findAll() {
        return models.findAllByOrderByProviderAscModelNameAsc().stream().map(JpaLlmModelStore::toRecord).toList();
    }

    @Override
    public Optional<LlmModelRecord> findById(long id) {
        return models.findById(id).map(JpaLlmModelStore::toRecord);
    }

    @Override
    public Optional<LlmModelRecord> findDefault() {
        return models.findByDefaultModelTrue().map(JpaLlmModelStore::toRecord);
    }

    @Override
    public LlmModelRecord save(LlmModelRecord model) {
        if (model.id() == null) {
            return toRecord(models.save(new LlmModelEntity(
                    model.provider(),
                    model.modelName(),
                    model.kind(),
                    model.enabled(),
                    model.defaultModel(),
                    model.contextWindow(),
                    model.costPer1kIn(),
                    model.costPer1kOut(),
                    model.notes(),
                    model.createdAt())));
        }
        LlmModelEntity entity = models.findById(model.id())
                .orElseThrow(() -> new IllegalStateException("registry row " + model.id() + " disappeared"));
        entity.edit(model.enabled(), model.defaultModel(), model.contextWindow(), model.costPer1kIn(),
                model.costPer1kOut(), model.notes(), model.updatedAt());
        return toRecord(models.save(entity));
    }

    private static LlmModelRecord toRecord(LlmModelEntity entity) {
        return new LlmModelRecord(
                entity.getId(),
                entity.getProvider(),
                entity.getModelName(),
                entity.getKind(),
                entity.isEnabled(),
                entity.isDefaultModel(),
                entity.getContextWindow(),
                entity.getCostPer1kIn(),
                entity.getCostPer1kOut(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
