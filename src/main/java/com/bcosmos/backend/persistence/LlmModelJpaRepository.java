package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelJpaRepository extends JpaRepository<LlmModelEntity, Long> {

    List<LlmModelEntity> findAllByOrderByProviderAscModelNameAsc();

    /**
     * The default row of one capability.
     *
     * <p>Scoped on purpose: the registry has a default chat model and a default embedding model, and a lookup that
     * ignored the capability would let an embedding model answer chat calls.</p>
     */
    Optional<LlmModelEntity> findByCapabilityAndDefaultModelTrue(LlmModelCapability capability);

    List<LlmModelEntity> findByCapabilityOrderByProviderAscModelNameAsc(LlmModelCapability capability);
}
