package com.bcosmos.backend.jobs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.JobJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.repository.MinedRepositoryAnalysisNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Mined repository analysis refusal (ADR 0024)")
class MinedRepoAnalysisRefusalTest {

    private final AnalysisRunJpaRepository runs = mock();
    private final JobJpaRepository jobs = mock();
    private final RepositoryJpaRepository repositories = mock();
    private final FindingJpaRepository findings = mock();
    private final SymbolJpaRepository symbols = mock();
    private final FileRecordJpaRepository fileRecords = mock();
    private final ApplicationEventPublisher events = mock();
    private final Clock clock = Clock.systemUTC();

    private final AnalysisRunService service = new AnalysisRunService(
            runs, jobs, repositories, findings, symbols, fileRecords, events, clock);

    @Test
    void refusingAnalysisOfMinedRepository() {
        RepositoryEntity minedRepo = RepositoryEntity.mined(
                "mined-sample", "https://github.com/sample/mined.git", "main", "Apache-2.0", Instant.now());
        when(repositories.findById(42L)).thenReturn(Optional.of(minedRepo));

        assertThatThrownBy(() -> service.start(42L))
                .isInstanceOf(MinedRepositoryAnalysisNotSupportedException.class)
                .hasMessageContaining("MINED repository and cannot be analysed on-demand");
    }
}
