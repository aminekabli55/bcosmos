package com.bcosmos.worker.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerFileResultJpaRepository extends JpaRepository<WorkerFileResultEntity, Long> {

    Optional<WorkerFileResultEntity> findByRepositoryIdAndCommitHashAndFilePathAndSideAndAnalyzerVersionAndRulesetHash(
            Long repositoryId, String commitHash, String filePath, String side, String analyzerVersion, String rulesetHash);
}
