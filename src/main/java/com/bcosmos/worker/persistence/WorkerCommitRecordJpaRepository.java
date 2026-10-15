package com.bcosmos.worker.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerCommitRecordJpaRepository extends JpaRepository<WorkerCommitRecordEntity, Long> {

    Optional<WorkerCommitRecordEntity> findByRepositoryIdAndHash(Long repositoryId, String hash);
}
