package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelJpaRepository extends JpaRepository<LlmModelEntity, Long> {

    List<LlmModelEntity> findAllByOrderByProviderAscModelNameAsc();

    Optional<LlmModelEntity> findByDefaultModelTrue();
}
