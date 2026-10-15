package com.bcosmos.worker.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerFileRecordJpaRepository extends JpaRepository<WorkerFileRecordEntity, Long> {

    Optional<WorkerFileRecordEntity> findByRunIdAndRelativePath(Long runId, String relativePath);

    List<WorkerFileRecordEntity> findByRunId(Long runId);

    void deleteByRunId(Long runId);
}
