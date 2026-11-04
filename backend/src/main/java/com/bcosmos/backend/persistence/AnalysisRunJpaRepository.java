package com.bcosmos.backend.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link AnalysisRunEntity}. */
public interface AnalysisRunJpaRepository extends JpaRepository<AnalysisRunEntity, Long> {

    Page<AnalysisRunEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId, Pageable pageable);

    java.util.Optional<AnalysisRunEntity> findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(
            Long repositoryId, String commitHash);

    /**
     * True when the repository has a run in one of the given states. Used by the deletion guard: while a run is
     * still going to write, the repository must not disappear underneath it (ADR 0037).
     */
    boolean existsByRepositoryIdAndStatusIn(Long repositoryId, java.util.Collection<AnalysisRunStatus> statuses);
}
