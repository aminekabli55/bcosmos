package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmProviderJpaRepository extends JpaRepository<LlmProviderEntity, Long> {

    Optional<LlmProviderEntity> findByCode(String code);

    List<LlmProviderEntity> findAllByOrderByCodeAsc();
}
