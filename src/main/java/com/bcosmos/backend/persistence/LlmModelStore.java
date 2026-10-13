package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;

/**
 * The narrow data-access port the model registry needs.
 *
 * <p>It exists so the registry can be unit-tested without a database: Spring Data's {@code JpaRepository} has
 * dozens of methods, and faking it would test the fake rather than the registry.
 * {@link JpaLlmModelStore} is the only implementation.</p>
 */
public interface LlmModelStore {

    /** Every registry row, ordered by provider and model name. */
    List<LlmModelRecord> findAll();

    /** One row by registry id. */
    Optional<LlmModelRecord> findById(long id);

    /** The row marked as default, if any. */
    Optional<LlmModelRecord> findDefault();

    /** Inserts a row with a {@code null} id, or updates the existing row with that id. */
    LlmModelRecord save(LlmModelRecord model);
}
