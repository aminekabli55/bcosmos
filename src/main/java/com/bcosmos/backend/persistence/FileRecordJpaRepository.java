package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.ParseStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link FileRecordEntity}. */
public interface FileRecordJpaRepository extends JpaRepository<FileRecordEntity, Long> {

    Page<FileRecordEntity> findByRunIdOrderByRelativePathAsc(Long runId, Pageable pageable);

    Page<FileRecordEntity> findByRunIdAndParseStatusOrderByRelativePathAsc(Long runId, ParseStatus parseStatus,
            Pageable pageable);

    List<FileRecordEntity> findByRunIdAndIdIn(Long runId, Collection<Long> ids);

    long countByRunIdAndParseStatus(Long runId, ParseStatus parseStatus);
}
