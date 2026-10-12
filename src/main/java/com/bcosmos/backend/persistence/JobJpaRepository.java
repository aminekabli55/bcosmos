package com.bcosmos.backend.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link JobEntity}. */
public interface JobJpaRepository extends JpaRepository<JobEntity, Long> {

    Optional<JobEntity> findFirstByRunIdOrderByIdDesc(Long runId);
}
