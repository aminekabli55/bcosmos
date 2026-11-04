package com.bcosmos.backend.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link BenchmarkRunEntity}.
 */
public interface BenchmarkRunJpaRepository extends JpaRepository<BenchmarkRunEntity, Long> {

    /** Returns all benchmark runs sorted by primary metric (target resolved rate desc). */
    List<BenchmarkRunEntity> findAllByOrderByTargetResolvedRateDesc();

    /** Returns runs for a specific dataset version sorted by target resolved rate desc. */
    List<BenchmarkRunEntity> findByDatasetVersionOrderByTargetResolvedRateDesc(String datasetVersion);
}
