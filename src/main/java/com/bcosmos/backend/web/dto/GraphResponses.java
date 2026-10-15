package com.bcosmos.backend.web.dto;

import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import com.bcosmos.core.model.SymbolKind;
import java.util.List;

public final class GraphResponses {

    private GraphResponses() {
    }

    public record GraphNode(
            String id,
            String name,
            String qualifiedName,
            SymbolKind kind,
            String filePath,
            int lineCount,
            int findingsCount,
            double hotspotScore,
            boolean isExternal,
            String packageName) {
    }

    public record GraphEdge(
            String id,
            String source,
            String target,
            RelationshipType type,
            ResolutionStatus status,
            double confidence,
            String evidenceFile,
            int evidenceLine) {
    }

    public record GraphResponse(
            Long analysisId,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            boolean truncated,
            int totalNodes,
            int totalEdges,
            int unresolvedEdgesCount,
            double resolutionRate) {
    }

    public record SymbolImpactResponse(
            Long symbolId,
            String qualifiedName,
            String filePath,
            int directDependentsCount,
            int transitiveDependentsCount,
            List<GraphNode> directDependents,
            List<GraphNode> transitiveDependents,
            int affectedFindingsCount,
            String riskRating) {
    }

    public record HotspotItem(
            Long symbolId,
            String name,
            String qualifiedName,
            String filePath,
            int lineCount,
            int findingsCount,
            int highSeverityCount,
            int inDegree,
            int outDegree,
            double score) {
    }

    public record HotspotListResponse(
            Long analysisId,
            List<HotspotItem> items,
            String heuristicFormula) {
    }
}
