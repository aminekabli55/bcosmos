package com.bcosmos.backend.graph;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.RelationshipEntity;
import com.bcosmos.backend.persistence.RelationshipJpaRepository;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.web.dto.GraphResponses.GraphResponse;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotListResponse;
import com.bcosmos.backend.web.dto.GraphResponses.SymbolImpactResponse;
import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private AnalysisRunJpaRepository runs;

    @Mock
    private SymbolJpaRepository symbols;

    @Mock
    private RelationshipJpaRepository relationships;

    @Mock
    private FindingJpaRepository findings;

    @InjectMocks
    private GraphService graphService;

    private AnalysisRunEntity testRun;
    private SymbolEntity s1;
    private SymbolEntity s2;
    private RelationshipEntity r1;

    @BeforeEach
    void setUp() {
        testRun = new AnalysisRunEntity(1L, com.bcosmos.backend.persistence.AnalysisRunStatus.SUCCEEDED, com.bcosmos.backend.persistence.AnalysisStage.COMPLETED, 1, Instant.now());
        ReflectionTestUtils.setField(testRun, "id", 10L);

        s1 = new SymbolEntity(10L, 100L, SymbolKind.CLASS, "UserService", "com.acme.UserService", null, new Span(1, 1, 50, 1), "sig1");
        ReflectionTestUtils.setField(s1, "id", 1L);

        s2 = new SymbolEntity(10L, 100L, SymbolKind.CLASS, "UserRepository", "com.acme.UserRepository", null, new Span(1, 1, 30, 1), "sig2");
        ReflectionTestUtils.setField(s2, "id", 2L);

        r1 = new RelationshipEntity(10L, 1L, 2L, "UserRepository", RelationshipType.USES_TYPE, ResolutionStatus.RESOLVED, 1.0, "UserService.java", 15);
        ReflectionTestUtils.setField(r1, "id", 50L);
    }

    @Test
    void getGraphBuildsNodesAndEdgesWithResolutionRate() {
        when(runs.findById(10L)).thenReturn(Optional.of(testRun));
        when(symbols.findByRunId(10L)).thenReturn(List.of(s1, s2));
        when(relationships.findByRunId(10L)).thenReturn(List.of(r1));
        when(findings.findByRunId(10L)).thenReturn(List.of());

        GraphResponse response = graphService.getGraph(10L, 2, 50, 100, null, null);

        assertThat(response.analysisId()).isEqualTo(10L);
        assertThat(response.nodes()).hasSize(2);
        assertThat(response.edges()).hasSize(1);
        assertThat(response.unresolvedEdgesCount()).isEqualTo(0);
        assertThat(response.resolutionRate()).isEqualTo(1.0);
    }

    @Test
    void getImpactComputesDependentsAndRiskRating() {
        when(symbols.findById(2L)).thenReturn(Optional.of(s2));
        when(relationships.findByRunId(10L)).thenReturn(List.of(r1));
        when(symbols.findById(1L)).thenReturn(Optional.of(s1));
        when(findings.findByRunId(10L)).thenReturn(List.of());

        SymbolImpactResponse impact = graphService.getImpact(2L);

        assertThat(impact.symbolId()).isEqualTo(2L);
        assertThat(impact.directDependentsCount()).isEqualTo(1);
        assertThat(impact.directDependents().getFirst().name()).isEqualTo("UserService");
        assertThat(impact.riskRating()).isEqualTo("LOW");
    }

    @Test
    void getHotspotsComputesCompositeScore() {
        when(runs.findById(10L)).thenReturn(Optional.of(testRun));
        when(symbols.findByRunId(10L)).thenReturn(List.of(s1, s2));
        when(relationships.findByRunId(10L)).thenReturn(List.of(r1));
        when(findings.findByRunId(10L)).thenReturn(List.of());

        HotspotListResponse hotspots = graphService.getHotspots(10L);

        assertThat(hotspots.analysisId()).isEqualTo(10L);
        assertThat(hotspots.items()).hasSize(2);
        assertThat(hotspots.heuristicFormula()).contains("score =");
    }
}
