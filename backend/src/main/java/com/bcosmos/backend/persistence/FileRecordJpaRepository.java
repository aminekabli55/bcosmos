package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.ParseStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@link FileRecordEntity}. */
public interface FileRecordJpaRepository extends JpaRepository<FileRecordEntity, Long> {

    Page<FileRecordEntity> findByRunIdOrderByRelativePathAsc(Long runId, Pageable pageable);

    Page<FileRecordEntity> findByRunIdAndParseStatusOrderByRelativePathAsc(Long runId, ParseStatus parseStatus,
            Pageable pageable);

    List<FileRecordEntity> findByRunIdAndIdIn(Long runId, Collection<Long> ids);

    /** Every file of one run; Phase 3 needs their paths and line counts to check citations. */
    List<FileRecordEntity> findByRunId(Long runId);

    long countByRunIdAndParseStatus(Long runId, ParseStatus parseStatus);

    /**
     * Files per language of one run, for the repository's primary-language census (ADR 0033). Aggregated in SQL so a
     * run over thousands of files does not have to load them all.
     */
    @Query("select f.language, count(f) from FileRecordEntity f where f.runId = :runId group by f.language")
    List<Object[]> countLanguagesByRunId(@Param("runId") Long runId);
}
