package com.bcosmos.worker.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerFindingJpaRepository extends JpaRepository<WorkerFindingEntity, Long> {

    List<WorkerFindingEntity> findByRunId(Long runId);

    List<WorkerFindingEntity> findByRunIdAndFileId(Long runId, Long fileId);

    void deleteByFileId(Long fileId);

    void deleteByRunId(Long runId);
}
