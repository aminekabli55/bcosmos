package com.bcosmos.worker.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepositoryJpaRepository extends JpaRepository<WorkerRepositoryEntity, Long> {
}
