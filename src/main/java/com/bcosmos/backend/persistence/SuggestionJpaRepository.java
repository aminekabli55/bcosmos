package com.bcosmos.backend.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@link SuggestionEntity}. */
public interface SuggestionJpaRepository extends JpaRepository<SuggestionEntity, Long> {

    /** Every suggestion stored for one finding, newest last: the review screen shows the history. */
    Page<SuggestionEntity> findByFindingIdOrderByCreatedAtAsc(Long findingId, Pageable pageable);

    /** The most recent suggestion of a finding, which is what the review screen opens with. */
    Optional<SuggestionEntity> findFirstByFindingIdOrderByCreatedAtDesc(Long findingId);

    List<SuggestionEntity> findByFindingId(Long findingId);
}
