package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.TestcontainersConfiguration;
import com.bcosmos.backend.jobs.AnalysisRunService;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.AnalysisRunStatus;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import com.bcosmos.core.model.ParseStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A finished run records the language its files were really written in (ADR 0033, spec v2 section 3.1). Registration
 * stores Java because it never reads the tree, so before this the repository list showed "Java · L3" for a
 * TypeScript project.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DisplayName("Repository primary language census (ADR 0033)")
class RepositoryLanguageCensusIntegrationTest {

    @Autowired
    private AnalysisRunService runs;

    @Autowired
    private AnalysisRunJpaRepository runRepository;

    @Autowired
    private RepositoryJpaRepository repositoryRepository;

    @Autowired
    private FileRecordJpaRepository fileRecords;

    @Test
    void a_typescript_run_relabels_the_repository() {
        RepositoryEntity repository = repositoryRepository.save(new RepositoryEntity(
                "ts-census-fixture", "C:\\fixtures\\ts-census", RepositorySourceType.LOCAL, "java",
                RepositoryStatus.REGISTERED, Instant.now()));
        Long runId = runRepository.save(new AnalysisRunEntity(repository.getId(), AnalysisRunStatus.RUNNING,
                AnalysisStage.ANALYSIS, AnalysisRunService.SCHEMA_VERSION, Instant.now())).getId();

        fileRecords.save(new FileRecordEntity(runId, "src/app/book.service.ts", "typescript", "h1", 12,
                ParseStatus.OK, null));
        fileRecords.save(new FileRecordEntity(runId, "src/app/book.model.ts", "typescript", "h2", 8,
                ParseStatus.OK, null));
        fileRecords.save(new FileRecordEntity(runId, "src/app/syntax-error.ts", "typescript", null, 0,
                ParseStatus.FAILED, "parse failed"));

        runs.finish(runId, 2, 1);

        RepositoryEntity reloaded = repositoryRepository.findById(repository.getId()).orElseThrow();
        assertThat(reloaded.getPrimaryLanguage()).isEqualTo("typescript");
        assertThat(reloaded.getStatus()).isEqualTo(RepositoryStatus.ANALYSED);
    }

    @Test
    void a_run_with_no_files_leaves_the_registered_language_alone() {
        RepositoryEntity repository = repositoryRepository.save(new RepositoryEntity(
                "empty-census-fixture", "C:\\fixtures\\empty-census", RepositorySourceType.LOCAL, "java",
                RepositoryStatus.REGISTERED, Instant.now()));
        Long runId = runRepository.save(new AnalysisRunEntity(repository.getId(), AnalysisRunStatus.RUNNING,
                AnalysisStage.ANALYSIS, AnalysisRunService.SCHEMA_VERSION, Instant.now())).getId();

        runs.finish(runId, 0, 0);

        assertThat(repositoryRepository.findById(repository.getId()).orElseThrow().getPrimaryLanguage())
                .isEqualTo("java");
    }
}
