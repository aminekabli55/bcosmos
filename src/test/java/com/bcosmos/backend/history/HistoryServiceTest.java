package com.bcosmos.backend.history;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.FindingTransitionEntity;
import com.bcosmos.backend.persistence.FindingTransitionJpaRepository;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.backend.web.dto.HistoryResponses.SnapshotComparisonResponse;
import com.bcosmos.core.model.FindingTransitionType;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private AnalysisRunJpaRepository runs;

    @Mock
    private FindingJpaRepository findings;

    @Mock
    private FileRecordJpaRepository fileRecords;

    @Mock
    private FindingTransitionJpaRepository transitions;

    @InjectMocks
    private HistoryService historyService;

    private AnalysisRunEntity fromRun;
    private AnalysisRunEntity toRun;

    @BeforeEach
    void setUp() {
        fromRun = new AnalysisRunEntity(1L, com.bcosmos.backend.persistence.AnalysisRunStatus.SUCCEEDED, com.bcosmos.backend.persistence.AnalysisStage.COMPLETED, 1, Instant.now());
        ReflectionTestUtils.setField(fromRun, "id", 100L);

        toRun = new AnalysisRunEntity(1L, com.bcosmos.backend.persistence.AnalysisRunStatus.SUCCEEDED, com.bcosmos.backend.persistence.AnalysisStage.COMPLETED, 1, Instant.now());
        ReflectionTestUtils.setField(toRun, "id", 200L);
    }

    @Test
    void compareSnapshotsComputesTransitionsCorrectly() {
        when(runs.findById(100L)).thenReturn(Optional.of(fromRun));
        when(runs.findById(200L)).thenReturn(Optional.of(toRun));
        when(transitions.findByFromRunIdAndToRunId(100L, 200L)).thenReturn(List.of());

        FileRecordEntity file1 = new FileRecordEntity(100L, "Foo.java", "java", "hash1", 50, ParseStatus.OK, null);
        ReflectionTestUtils.setField(file1, "id", 10L);
        FileRecordEntity file2 = new FileRecordEntity(200L, "Foo.java", "java", "hash2", 60, ParseStatus.OK, null);
        ReflectionTestUtils.setField(file2, "id", 20L);

        when(fileRecords.findByRunId(100L)).thenReturn(List.of(file1));
        when(fileRecords.findByRunId(200L)).thenReturn(List.of(file2));

        FindingEntity f1 = new FindingEntity(100L, 10L, "pmd", "7.0", "EmptyCatchBlock", "HIGH", Severity.HIGH,
                "msg", new Span(5, 1, 7, 1), "foo", "code", "code", "fp-1", TriageStatus.OPEN, Instant.now());
        ReflectionTestUtils.setField(f1, "id", 1L);

        FindingEntity f2 = new FindingEntity(200L, 20L, "pmd", "7.0", "EmptyCatchBlock", "HIGH", Severity.HIGH,
                "msg", new Span(5, 1, 7, 1), "foo", "code", "code", "fp-1", TriageStatus.OPEN, Instant.now());
        ReflectionTestUtils.setField(f2, "id", 2L);

        when(findings.findByRunId(100L)).thenReturn(List.of(f1));
        when(findings.findByRunId(200L)).thenReturn(List.of(f2));

        when(transitions.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        SnapshotComparisonResponse response = historyService.compareSnapshots(100L, 200L);

        assertThat(response.fromRun().id()).isEqualTo(100L);
        assertThat(response.toRun().id()).isEqualTo(200L);
        assertThat(response.persistentCount()).isEqualTo(1);
        assertThat(response.resolvedCount()).isEqualTo(0);
        assertThat(response.newCount()).isEqualTo(0);
        assertThat(response.transitions().getFirst().type()).isEqualTo(FindingTransitionType.PERSISTENT);
    }
}
