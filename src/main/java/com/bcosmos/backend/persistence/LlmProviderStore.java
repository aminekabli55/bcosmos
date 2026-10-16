package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;

/**
 * The narrow data-access port the provider registry needs.
 *
 * <p>Same reasoning as {@link LlmModelStore}: the registry is unit-tested against a fake, so the port stays small
 * and {@link JpaLlmProviderStore} is its only implementation.</p>
 */
public interface LlmProviderStore {

    /** Every provider, ordered by code. */
    List<LlmProviderRecord> findAll();

    /** One provider by id. */
    Optional<LlmProviderRecord> findById(long id);

    /** One provider by its stable code. */
    Optional<LlmProviderRecord> findByCode(String code);

    /** Inserts a row with a {@code null} id, or applies the record to the existing row with that id. */
    LlmProviderRecord save(LlmProviderRecord provider);

    /** Removes a provider row. The caller refuses this while models still reference it. */
    void delete(long id);
}
