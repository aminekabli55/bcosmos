package com.bcosmos.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link FileContentEntity}. */
public interface FileContentJpaRepository extends JpaRepository<FileContentEntity, Long> {
}
