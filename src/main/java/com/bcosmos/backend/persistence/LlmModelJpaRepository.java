package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelJpaRepository extends JpaRepository<LlmModelEntity, Long> {

    /** Ordered by provider id: providers are inserted in a deliberate order, so this groups vendors as seeded. */
    List<LlmModelEntity> findAllByOrderByProviderIdAscModelNameAsc();

    /**
     * The default row of one capability.
     *
     * <p>Scoped on purpose: the registry has a default chat model and a default embedding model, and a lookup that
     * ignored the capability would let an embedding model answer chat calls.</p>
     */
    Optional<LlmModelEntity> findByCapabilityAndDefaultModelTrue(LlmModelCapability capability);

    List<LlmModelEntity> findByCapabilityOrderByProviderIdAscModelNameAsc(LlmModelCapability capability);

    /** The (provider, model name) pair is unique; the registry refuses a duplicate with a clear message. */
    boolean existsByProviderIdAndModelName(long providerId, String modelName);

    long countByProviderId(long providerId);
}
