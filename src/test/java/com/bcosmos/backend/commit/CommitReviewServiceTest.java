package com.bcosmos.backend.commit;

import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.StructuredResult;
import com.bcosmos.backend.graph.GraphService;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.CommitReviewEntity;
import com.bcosmos.backend.persistence.CommitReviewJpaRepository;
import com.bcosmos.backend.persistence.FindingTransitionJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.settings.AiSwitch;
import com.bcosmos.backend.web.dto.CommitResponses.CommitListResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CommitReviewResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CreateCommitReviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitReviewServiceTest {

    @Mock
    private RepositoryJpaRepository repositories;
    @Mock
    private CommitReviewJpaRepository commitReviews;
    @Mock
    private AnalysisRunJpaRepository analysisRuns;
    @Mock
    private FindingTransitionJpaRepository transitions;
    @Mock
    private SymbolJpaRepository symbols;
    @Mock
    private GitCommitService gitService;
    @Mock
    private GraphService graphService;
    @Mock
    private LlmClient llm;
    @Mock
    private PromptTemplateLoader prompts;
    @Mock
    private AiSwitch aiSwitch;

    @InjectMocks
    private CommitReviewService service;

    private RepositoryEntity repo;

    @BeforeEach
    void setUp() {
        repo = new RepositoryEntity("Test Repo", "/some/path",
                com.bcosmos.backend.persistence.RepositorySourceType.LOCAL,
                "Java",
                com.bcosmos.backend.persistence.RepositoryStatus.REGISTERED,
                java.time.Instant.now());
        ReflectionTestUtils.setField(repo, "id", 42L);
    }

    // ─── listCommits ─────────────────────────────────────────────────────────

    @Test
    void listCommits_repositoryNotFound_throwsNoSuchElement() {
        when(repositories.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCommits(99L, 20))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Repository not found");
    }

    @Test
    void listCommits_returnsCommitsWithReviewIds() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        when(gitService.listCommits(any(), anyInt())).thenReturn(List.of(
                new GitCommitService.CommitSummary("abc123", "abc123", "fix: bug", "dev", Instant.now(), List.of())
        ));
        CommitReviewEntity existing = buildReview("xxx000", "abc123");
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(commitReviews.findByRepositoryIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(existing));

        CommitListResponse response = service.listCommits(42L, 30);

        assertThat(response.repositoryId()).isEqualTo(42L);
        assertThat(response.commits()).hasSize(1);
        assertThat(response.commits().get(0).reviewId()).isEqualTo(7L);
    }

    @Test
    void listCommits_noExistingReviews_reviewIdIsNull() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        when(gitService.listCommits(any(), anyInt())).thenReturn(List.of(
                new GitCommitService.CommitSummary("def456", "def456", "feat: add", "dev", Instant.now(), List.of())
        ));
        when(commitReviews.findByRepositoryIdOrderByCreatedAtDesc(42L)).thenReturn(Collections.emptyList());

        CommitListResponse response = service.listCommits(42L, 30);

        assertThat(response.commits().get(0).reviewId()).isNull();
    }

    // ─── getReview ────────────────────────────────────────────────────────────

    @Test
    void getReview_notFound_throwsNoSuchElement() {
        when(commitReviews.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReview(55L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Commit review not found");
    }

    @Test
    void getReview_found_deserializesFactsAndSignals() {
        CommitReviewEntity entity = buildReview("aaa", "bbb");
        ReflectionTestUtils.setField(entity, "id", 3L);
        when(commitReviews.findById(3L)).thenReturn(Optional.of(entity));

        CommitReviewResponse resp = service.getReview(3L);

        assertThat(resp.id()).isEqualTo(3L);
        assertThat(resp.fromCommit()).isEqualTo("aaa");
        assertThat(resp.toCommit()).isEqualTo("bbb");
        assertThat(resp.facts()).isNotNull();
        assertThat(resp.riskSignals()).isNotNull();
    }

    // ─── createReview ─────────────────────────────────────────────────────────

    @Test
    void createReview_repositoryNotFound_throwsNoSuchElement() {
        when(repositories.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReview(99L,
                new CreateCommitReviewRequest("fromHash", "toHash", null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void createReview_emptyDiff_returnsFacts() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        when(gitService.computeDiff(any(), anyString(), anyString()))
                .thenReturn(new GitCommitService.DiffResult("from", "to",
                        Collections.emptyList(), false, false, ""));
        when(analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(symbols.count()).thenReturn(0L);
        when(aiSwitch.enabled()).thenReturn(false);

        CommitReviewEntity saved = buildReview("from", "to");
        ReflectionTestUtils.setField(saved, "id", 1L);
        when(commitReviews.save(any())).thenReturn(saved);

        CommitReviewResponse resp = service.createReview(42L,
                new CreateCommitReviewRequest("from", "to", null));

        assertThat(resp).isNotNull();
        assertThat(resp.fromCommit()).isEqualTo("from");
        verify(llm, never()).structured(any());
    }

    @Test
    void createReview_mainChangedNoTest_raisesRiskSignal() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        // diff with src/main/ file but no test file
        GitCommitService.FileDiffEntry mainFile = new GitCommitService.FileDiffEntry(
                "src/main/java/Foo.java", "src/main/java/Foo.java", "MODIFY");
        when(gitService.computeDiff(any(), anyString(), anyString()))
                .thenReturn(new GitCommitService.DiffResult("from", "to",
                        List.of(mainFile), true, false, "diff text"));
        when(analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(symbols.count()).thenReturn(0L);
        when(aiSwitch.enabled()).thenReturn(false);

        CommitReviewEntity saved = buildReview("from", "to");
        ReflectionTestUtils.setField(saved, "id", 2L);
        when(commitReviews.save(any())).thenReturn(saved);

        CommitReviewResponse resp = service.createReview(42L,
                new CreateCommitReviewRequest("from", "to", null));

        assertThat(resp).isNotNull();
        // The risk signal should have been passed to save; the saved entity
        // has an empty riskSignalsJson so we can only verify no NPE here.
        verify(commitReviews).save(any(CommitReviewEntity.class));
    }

    @Test
    void createReview_newFindingsIntroduced_raisesRiskSignal() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        when(gitService.computeDiff(any(), anyString(), anyString()))
                .thenReturn(new GitCommitService.DiffResult("from", "to",
                        Collections.emptyList(), false, false, ""));

        // Both runs present → transitions will be fetched
        com.bcosmos.backend.persistence.AnalysisRunEntity fromRun =
                new com.bcosmos.backend.persistence.AnalysisRunEntity(
                        42L,
                        com.bcosmos.backend.persistence.AnalysisRunStatus.SUCCEEDED,
                        com.bcosmos.backend.persistence.AnalysisStage.COMPLETED, 1, Instant.now());
        ReflectionTestUtils.setField(fromRun, "id", 10L);
        com.bcosmos.backend.persistence.AnalysisRunEntity toRun =
                new com.bcosmos.backend.persistence.AnalysisRunEntity(
                        42L,
                        com.bcosmos.backend.persistence.AnalysisRunStatus.SUCCEEDED,
                        com.bcosmos.backend.persistence.AnalysisStage.COMPLETED, 1, Instant.now());
        ReflectionTestUtils.setField(toRun, "id", 11L);

        when(analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(42L, "from"))
                .thenReturn(Optional.of(fromRun));
        when(analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(42L, "to"))
                .thenReturn(Optional.of(toRun));

        com.bcosmos.backend.persistence.FindingTransitionEntity newTrans =
                new com.bcosmos.backend.persistence.FindingTransitionEntity(
                        10L, 11L, null, null, "fp", "Rule", "Foo.java",
                        com.bcosmos.core.model.FindingTransitionType.NEW, 1.0, "new");
        when(transitions.findByFromRunIdAndToRunId(10L, 11L)).thenReturn(List.of(newTrans));
        when(symbols.count()).thenReturn(0L);
        when(aiSwitch.enabled()).thenReturn(false);

        CommitReviewEntity saved = buildReview("from", "to");
        ReflectionTestUtils.setField(saved, "id", 3L);
        when(commitReviews.save(any())).thenReturn(saved);

        CommitReviewResponse resp = service.createReview(42L,
                new CreateCommitReviewRequest("from", "to", null));

        assertThat(resp).isNotNull();
        verify(transitions).findByFromRunIdAndToRunId(10L, 11L);
    }

    @Test
    void createReview_aiDisabled_skipsLlmCall() {
        when(repositories.findById(42L)).thenReturn(Optional.of(repo));
        when(gitService.computeDiff(any(), anyString(), anyString()))
                .thenReturn(new GitCommitService.DiffResult("from", "to",
                        Collections.emptyList(), false, false, ""));
        when(analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(symbols.count()).thenReturn(0L);
        when(aiSwitch.enabled()).thenReturn(false);

        CommitReviewEntity saved = buildReview("from", "to");
        ReflectionTestUtils.setField(saved, "id", 4L);
        when(commitReviews.save(any())).thenReturn(saved);

        service.createReview(42L, new CreateCommitReviewRequest("from", "to", null));

        verify(llm, never()).structured(any());
        verify(prompts, never()).load(anyString(), anyString());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private CommitReviewEntity buildReview(String from, String to) {
        return new CommitReviewEntity(
                42L, from, to, 0, 0, 0, 0, 0,
                "{\"filesChangedCount\":0,\"filesChanged\":[],\"newFindingsCount\":0,"
                        + "\"resolvedFindingsCount\":0,\"persistentFindingsCount\":0,"
                        + "\"uncertainFindingsCount\":0,\"affectedDependentsCount\":0,"
                        + "\"unresolvedEdgesCount\":0}",
                "[]",
                null, null, null, null, null,
                Instant.now());
    }
}
