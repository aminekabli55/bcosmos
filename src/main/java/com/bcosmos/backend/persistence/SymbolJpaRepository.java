package com.bcosmos.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link SymbolEntity}. */
public interface SymbolJpaRepository extends JpaRepository<SymbolEntity, Long> {

    long countByRunId(Long runId);

    long countByFileId(Long fileId);
}
