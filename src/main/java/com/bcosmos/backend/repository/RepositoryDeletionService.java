package com.bcosmos.backend.repository;

import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a registered repository and everything derived from it (ADR 0037).
 *
 * <p>Deletion is one database statement: every table that hangs off {@code repository} — directly
 * ({@code analysis_run}, {@code commit_record}, {@code worker_file_result}, {@code commit_review}) or through
 * {@code analysis_run} (jobs, files, symbols, findings, suggestions, chunks, relationships, transitions) — already
 * carries {@code ON DELETE CASCADE} (changelog 0024 completed the last one). The service therefore never walks the
 * children itself: the cascading delete is atomic, and a list of child tables maintained here would silently rot the
 * first time a phase adds another one.</p>
 *
 * <p>The repository folder on disk is not touched. BCosmos only ever reads it (spec section 0 rule 4), so removing
 * the registration removes knowledge of the project, never the project.</p>
 */
@Service
public class RepositoryDeletionService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryDeletionService.class);

    /** A run in either of these states is still going to write, so the repository is not deletable yet. */
    private static final EnumSet<AnalysisRunStatus> IN_FLIGHT =
            EnumSet.of(AnalysisRunStatus.PENDING, AnalysisRunStatus.RUNNING);

    private final RepositoryJpaRepository repositories;
    private final AnalysisRunJpaRepository runs;

    public RepositoryDeletionService(RepositoryJpaRepository repositories, AnalysisRunJpaRepository runs) {
        this.repositories = repositories;
        this.runs = runs;
    }

    /**
     * Deletes one repository and its derived rows.
     *
     * @throws RepositoryNotFoundException when no repository has that id (unknown and already-deleted are the same
     *                                     answer to the caller: 404)
     * @throws RepositoryBusyException when an analysis of it has not reached a terminal state
     */
    @Transactional
    public void delete(long repositoryId) {
        RepositoryEntity repository = repositories.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));

        if (runs.existsByRepositoryIdAndStatusIn(repositoryId, IN_FLIGHT)) {
            throw new RepositoryBusyException(
                    "this repository has an analysis that has not finished yet; wait for it to finish or fail, "
                            + "then delete it");
        }

        repositories.delete(repository);
        // Id only: the name and the path are repository metadata, and the path is never logged (spec section 10).
        log.info("removed repository {} and every row derived from it", repositoryId);
    }
}
