package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link CommitRecordEntity}. */
public interface CommitRecordJpaRepository extends JpaRepository<CommitRecordEntity, Long> {

    /** The idempotency key of the ingest side: one row per (repository, commit), however often it is delivered. */
    Optional<CommitRecordEntity> findByRepositoryIdAndHash(Long repositoryId, String hash);

    long countByRepositoryId(Long repositoryId);

    Optional<CommitRecordEntity> findFirstByRepositoryIdOrderByCommittedAtDesc(Long repositoryId);

    /** The most recent commits of a repository, newest first; the status screen shows these. */
    List<CommitRecordEntity> findTop20ByRepositoryIdOrderByCommittedAtDesc(Long repositoryId);
}
