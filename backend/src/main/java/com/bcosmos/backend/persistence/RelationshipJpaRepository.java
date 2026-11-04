package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.ResolutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RelationshipJpaRepository extends JpaRepository<RelationshipEntity, Long> {

    List<RelationshipEntity> findByRunId(Long runId);

    List<RelationshipEntity> findBySourceSymbolId(Long sourceSymbolId);

    List<RelationshipEntity> findByTargetSymbolId(Long targetSymbolId);

    long countByRunId(Long runId);

    long countByRunIdAndResolutionStatus(Long runId, ResolutionStatus status);
}
