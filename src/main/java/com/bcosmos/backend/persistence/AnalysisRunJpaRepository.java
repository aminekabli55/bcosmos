package com.bcosmos.backend.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link AnalysisRunEntity}. */
public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, Long> {

    Page<AnalysisRunEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId, Pageable pageable);
}
