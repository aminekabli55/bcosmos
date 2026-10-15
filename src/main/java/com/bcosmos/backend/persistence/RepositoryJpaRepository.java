package com.bcosmos.backend.persistence;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link RepositoryEntity}. */
public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, Long> {

    Optional<RepositoryEntity> findByCanonicalLocalPath(String canonicalLocalPath);

    /** A mined repository is identified by its remote URL: it has no local path of its own (changelog 0016). */
    Optional<RepositoryEntity> findByRemoteUrl(String remoteUrl);

    Page<RepositoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
