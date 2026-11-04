package com.bcosmos.backend.persistence;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@link LlmUsageEntity}.
 *
 * <p>Write-only for now: Phase 6 reads usage per benchmark run, and the settings screen shows a spend hint. Until
 * then the rows are asserted from tests, not from the application.</p>
 */
public interface LlmUsageJpaRepository extends JpaRepository<LlmUsageEntity, Long> {

    long countByModelId(Long modelId);

    /** Sum of the estimated cost of one model's calls, for the registry's spend hint. */
    @Query("select coalesce(sum(u.estimatedCostUsd), 0) from LlmUsageEntity u where u.modelId = :modelId")
    BigDecimal totalEstimatedCostOf(@Param("modelId") Long modelId);
}
