package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The two refusals and the happy path of repository deletion (ADR 0037), without a database. */
@DisplayName("Repository deletion (ADR 0037)")
class RepositoryDeletionServiceTest {

    private final RepositoryJpaRepository repositories = mock();
    private final AnalysisRunJpaRepository runs = mock();

    private final RepositoryDeletionService service = new RepositoryDeletionService(repositories, runs);

    private static RepositoryEntity repository(String name) {
        return new RepositoryEntity(name, "c:/repos/" + name, RepositorySourceType.LOCAL, "java",
                RepositoryStatus.ANALYSED, Instant.now());
    }

    @Test
    void refusesToDeleteWhileARunIsStillInFlight() {
        RepositoryEntity busy = repository("busy-repo");
        when(repositories.findById(7L)).thenReturn(Optional.of(busy));
        when(runs.existsByRepositoryIdAndStatusIn(anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(RepositoryBusyException.class)
                .hasMessageContaining("has not finished yet");

        verify(repositories, never()).delete(any(RepositoryEntity.class));
    }

    @Test
    void reportsAnUnknownRepositoryRatherThanSilentlySucceeding() {
        when(repositories.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(RepositoryNotFoundException.class);

        verify(runs, never()).existsByRepositoryIdAndStatusIn(anyLong(), any());
        verify(repositories, never()).delete(any(RepositoryEntity.class));
    }

    @Test
    void deletesTheRepositoryItselfAndLeavesTheCascadeToTheDatabase() {
        RepositoryEntity repository = repository("java-fixture");
        when(repositories.findById(1L)).thenReturn(Optional.of(repository));

        service.delete(1L);

        verify(repositories).delete(repository);
    }

    /** The guard asks about exactly the two states that still write: a finished run must never block a delete. */
    @Test
    void theInFlightGuardCoversOnlyPendingAndRunningRuns() {
        when(repositories.findById(9L)).thenReturn(Optional.of(repository("done-repo")));

        service.delete(9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<AnalysisRunStatus>> states = ArgumentCaptor.forClass(Collection.class);
        verify(runs).existsByRepositoryIdAndStatusIn(eq(9L), states.capture());
        assertThat(states.getValue())
                .containsExactlyInAnyOrder(AnalysisRunStatus.PENDING, AnalysisRunStatus.RUNNING);
    }
}
