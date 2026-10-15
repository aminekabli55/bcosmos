package com.bcosmos.backend.persistence;

import com.bcosmos.core.model.Severity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@link FindingEntity}. */
public interface FindingJpaRepository extends JpaRepository<FindingEntity, Long> {

    long countByRunId(Long runId);

    List<FindingEntity> findByRunId(Long runId);

    /**
     * Findings of one run, optionally filtered by severity, rule and file (spec v2 section 8). A null filter means
     * "no filter"; the file filter is a substring match on the repository-relative path, case-insensitive.
     */
    @Query("""
            select f from FindingEntity f
            where f.runId = :runId
              and (:severity is null or f.severity = :severity)
              and (:ruleId is null or f.ruleId = :ruleId)
              and (:filePattern is null or f.fileId in (
                    select fr.id from FileRecordEntity fr
                    where fr.runId = :runId and lower(fr.relativePath) like :filePattern))
            """)
    Page<FindingEntity> search(
            @Param("runId") Long runId,
            @Param("severity") Severity severity,
            @Param("ruleId") String ruleId,
            @Param("filePattern") String filePattern,
            Pageable pageable);
}
