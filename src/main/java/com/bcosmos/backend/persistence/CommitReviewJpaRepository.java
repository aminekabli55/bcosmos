package com.bcosmos.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitReviewJpaRepository extends JpaRepository<CommitReviewEntity, Long> {

    List<CommitReviewEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    Optional<CommitReviewEntity> findByRepositoryIdAndFromCommitHashAndToCommitHash(
            Long repositoryId, String fromCommitHash, String toCommitHash);

    Optional<CommitReviewEntity> findFirstByRepositoryIdAndToCommitHashOrderByCreatedAtDesc(
            Long repositoryId, String toCommitHash);
}
