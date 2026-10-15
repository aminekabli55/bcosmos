package com.bcosmos.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FindingTransitionJpaRepository extends JpaRepository<FindingTransitionEntity, Long> {

    List<FindingTransitionEntity> findByFromRunIdAndToRunId(Long fromRunId, Long toRunId);
}
