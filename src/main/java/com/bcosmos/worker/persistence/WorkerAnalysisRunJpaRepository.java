package com.bcosmos.worker.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerAnalysisRunJpaRepository extends JpaRepository<WorkerAnalysisRunEntity, Long> {

    Optional<WorkerAnalysisRunEntity> findByRepositoryIdAndCommitHash(Long repositoryId, String commitHash);
}
