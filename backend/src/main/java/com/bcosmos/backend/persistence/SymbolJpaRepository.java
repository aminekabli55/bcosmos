package com.bcosmos.backend.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link SymbolEntity}. */
public interface SymbolJpaRepository extends JpaRepository<SymbolEntity, Long> {

    /** Symbols of one run, in file and position order: the order chunking and retrieval read them in. */
    List<SymbolEntity> findByRunIdOrderByFileIdAscStartLineAsc(Long runId);

    List<SymbolEntity> findByRunId(Long runId);

    java.util.Optional<SymbolEntity> findByRunIdAndQualifiedName(Long runId, String qualifiedName);

    long countByRunId(Long runId);

    long countByFileId(Long fileId);
}
