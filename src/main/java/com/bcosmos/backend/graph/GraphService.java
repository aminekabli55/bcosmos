package com.bcosmos.backend.graph;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.RelationshipEntity;
import com.bcosmos.backend.persistence.RelationshipJpaRepository;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.web.dto.GraphResponses.GraphEdge;
import com.bcosmos.backend.web.dto.GraphResponses.GraphNode;
import com.bcosmos.backend.web.dto.GraphResponses.GraphResponse;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotItem;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotListResponse;
import com.bcosmos.backend.web.dto.GraphResponses.SymbolImpactResponse;
import com.bcosmos.core.model.HotspotScore;
import com.bcosmos.core.model.ResolutionStatus;
import com.bcosmos.core.model.Severity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

/**
 * Provides bounded 2D graph queries, impact analysis, and triage hotspot ranking (Phase 8, spec §3.2 & 7.7).
 */
@Service
@Transactional(readOnly = true)
public class GraphService {

    private static final int DEFAULT_MAX_NODES = 150;
    private static final int MAX_NODE_LIMIT = 500;
    private static final int DEFAULT_MAX_EDGES = 300;
    private static final int MAX_EDGE_LIMIT = 1000;
    private static final int DEFAULT_DEPTH = 2;
    private static final int MAX_DEPTH_LIMIT = 5;

    private final AnalysisRunJpaRepository runs;
    private final SymbolJpaRepository symbols;
    private final RelationshipJpaRepository relationships;
    private final FindingJpaRepository findings;

    public GraphService(
            AnalysisRunJpaRepository runs,
            SymbolJpaRepository symbols,
            RelationshipJpaRepository relationships,
            FindingJpaRepository findings) {
        this.runs = runs;
        this.symbols = symbols;
        this.relationships = relationships;
        this.findings = findings;
    }

    public GraphResponse getGraph(
            Long analysisId,
            Integer depth,
            Integer maxNodes,
            Integer maxEdges,
            String packageFilter,
            Long focusSymbolId) {

        AnalysisRunEntity run = runs.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis run not found: " + analysisId));

        int nodeCap = Math.min(maxNodes != null && maxNodes > 0 ? maxNodes : DEFAULT_MAX_NODES, MAX_NODE_LIMIT);
        int edgeCap = Math.min(maxEdges != null && maxEdges > 0 ? maxEdges : DEFAULT_MAX_EDGES, MAX_EDGE_LIMIT);
        int maxDepth = Math.min(depth != null && depth > 0 ? depth : DEFAULT_DEPTH, MAX_DEPTH_LIMIT);

        List<SymbolEntity> allSymbols = symbols.findByRunId(analysisId);
        List<RelationshipEntity> allEdges = relationships.findByRunId(analysisId);
        List<FindingEntity> allFindings = findings.findByRunId(analysisId);

        // Precompute findings count per symbol qualified name
        Map<String, List<FindingEntity>> findingsBySymbol = new HashMap<>();
        for (FindingEntity f : allFindings) {
            if (f.getSymbolQualifiedName() != null) {
                findingsBySymbol.computeIfAbsent(f.getSymbolQualifiedName(), k -> new ArrayList<>()).add(f);
            }
        }

        // Degree maps
        Map<Long, Integer> inDegreeMap = new HashMap<>();
        Map<Long, Integer> outDegreeMap = new HashMap<>();
        for (RelationshipEntity rel : allEdges) {
            outDegreeMap.merge(rel.getSourceSymbolId(), 1, Integer::sum);
            if (rel.getTargetSymbolId() != null) {
                inDegreeMap.merge(rel.getTargetSymbolId(), 1, Integer::sum);
            }
        }

        // Filter / Traverse
        Set<Long> includedSymbolIds = new HashSet<>();
        if (focusSymbolId != null) {
            // BFS traversal around focus symbol
            traverseAround(focusSymbolId, maxDepth, nodeCap, allEdges, includedSymbolIds);
        } else {
            for (SymbolEntity s : allSymbols) {
                if (packageFilter == null || packageFilter.isBlank() || extractPackage(s.getQualifiedName()).startsWith(packageFilter)) {
                    includedSymbolIds.add(s.getId());
                    if (includedSymbolIds.size() >= nodeCap) {
                        break;
                    }
                }
            }
        }

        boolean truncated = includedSymbolIds.size() < allSymbols.size();

        // Build GraphNode list
        Map<Long, SymbolEntity> symbolById = new HashMap<>();
        for (SymbolEntity s : allSymbols) {
            symbolById.put(s.getId(), s);
        }

        List<GraphNode> nodes = new ArrayList<>();
        Set<String> externalNodeIds = new HashSet<>();

        for (Long id : includedSymbolIds) {
            SymbolEntity s = symbolById.get(id);
            if (s != null) {
                List<FindingEntity> symFindings = findingsBySymbol.getOrDefault(s.getQualifiedName(), Collections.emptyList());
                int highCount = (int) symFindings.stream()
                        .filter(f -> f.getSeverity() == Severity.HIGH || f.getSeverity() == Severity.CRITICAL)
                        .count();
                int inDegree = inDegreeMap.getOrDefault(s.getId(), 0);
                int outDegree = outDegreeMap.getOrDefault(s.getId(), 0);
                int lines = s.getSpan().lineCount();
                double hotspot = HotspotScore.calculate(symFindings.size(), highCount, inDegree, lines);

                nodes.add(new GraphNode(
                        String.valueOf(s.getId()),
                        s.getName(),
                        s.getQualifiedName(),
                        s.getKind(),
                        "", // filePath populated when available
                        lines,
                        symFindings.size(),
                        hotspot,
                        false,
                        extractPackage(s.getQualifiedName())));
            }
        }

        // Build GraphEdge list
        List<GraphEdge> edges = new ArrayList<>();
        int unresolvedCount = 0;

        for (RelationshipEntity rel : allEdges) {
            if (edges.size() >= edgeCap) {
                truncated = true;
                break;
            }

            boolean sourceIncluded = includedSymbolIds.contains(rel.getSourceSymbolId());
            if (!sourceIncluded) {
                continue;
            }

            String targetNodeId;
            if (rel.getTargetSymbolId() != null) {
                if (!includedSymbolIds.contains(rel.getTargetSymbolId()) && nodes.size() < nodeCap) {
                    SymbolEntity targetSym = symbolById.get(rel.getTargetSymbolId());
                    if (targetSym != null) {
                        includedSymbolIds.add(targetSym.getId());
                        nodes.add(toGraphNode(targetSym, findingsBySymbol, inDegreeMap, outDegreeMap));
                    }
                }
                targetNodeId = String.valueOf(rel.getTargetSymbolId());
            } else {
                // External unresolved node
                targetNodeId = "ext:" + rel.getTargetReferenceName();
                if (externalNodeIds.add(targetNodeId)) {
                    nodes.add(new GraphNode(
                            targetNodeId,
                            rel.getTargetReferenceName(),
                            rel.getTargetReferenceName(),
                            null,
                            "",
                            0,
                            0,
                            0.0,
                            true,
                            "(external)"));
                }
            }

            if (rel.getResolutionStatus() != ResolutionStatus.RESOLVED) {
                unresolvedCount++;
            }

            edges.add(new GraphEdge(
                    String.valueOf(rel.getId()),
                    String.valueOf(rel.getSourceSymbolId()),
                    targetNodeId,
                    rel.getType(),
                    rel.getResolutionStatus(),
                    rel.getConfidence(),
                    rel.getEvidenceFile(),
                    rel.getEvidenceLine()));
        }

        double resolutionRate = allEdges.isEmpty() ? 1.0 : (double) (allEdges.size() - unresolvedCount) / allEdges.size();

        return new GraphResponse(
                analysisId,
                nodes,
                edges,
                truncated,
                allSymbols.size(),
                allEdges.size(),
                unresolvedCount,
                Math.round(resolutionRate * 1000.0) / 1000.0);
    }

    public List<GraphNode> getDependencies(Long symbolId) {
        SymbolEntity source = symbols.findById(symbolId)
                .orElseThrow(() -> new NoSuchElementException("Symbol not found: " + symbolId));
        List<RelationshipEntity> outgoing = relationships.findBySourceSymbolId(symbolId);

        List<GraphNode> result = new ArrayList<>();
        for (RelationshipEntity rel : outgoing) {
            if (rel.getTargetSymbolId() != null) {
                symbols.findById(rel.getTargetSymbolId()).ifPresent(t -> result.add(toSimpleNode(t)));
            } else {
                result.add(new GraphNode(
                        "ext:" + rel.getTargetReferenceName(),
                        rel.getTargetReferenceName(),
                        rel.getTargetReferenceName(),
                        null,
                        rel.getEvidenceFile(),
                        0, 0, 0.0, true, "(external)"));
            }
        }
        return result;
    }

    public List<GraphNode> getDependents(Long symbolId) {
        SymbolEntity target = symbols.findById(symbolId)
                .orElseThrow(() -> new NoSuchElementException("Symbol not found: " + symbolId));
        List<RelationshipEntity> incoming = relationships.findByTargetSymbolId(symbolId);

        List<GraphNode> result = new ArrayList<>();
        for (RelationshipEntity rel : incoming) {
            symbols.findById(rel.getSourceSymbolId()).ifPresent(s -> result.add(toSimpleNode(s)));
        }
        return result;
    }

    public SymbolImpactResponse getImpact(Long symbolId) {
        SymbolEntity target = symbols.findById(symbolId)
                .orElseThrow(() -> new NoSuchElementException("Symbol not found: " + symbolId));

        // BFS upstream (who depends on target)
        List<RelationshipEntity> runEdges = relationships.findByRunId(target.getRunId());
        Map<Long, List<Long>> incomingAdjacency = new HashMap<>();
        for (RelationshipEntity r : runEdges) {
            if (r.getTargetSymbolId() != null) {
                incomingAdjacency.computeIfAbsent(r.getTargetSymbolId(), k -> new ArrayList<>()).add(r.getSourceSymbolId());
            }
        }

        List<SymbolEntity> directDependents = new ArrayList<>();
        List<SymbolEntity> transitiveDependents = new ArrayList<>();

        Set<Long> visited = new HashSet<>();
        visited.add(symbolId);

        List<Long> directIds = incomingAdjacency.getOrDefault(symbolId, Collections.emptyList());
        for (Long dId : directIds) {
            if (visited.add(dId)) {
                symbols.findById(dId).ifPresent(directDependents::add);
            }
        }

        Queue<Long> queue = new ArrayDeque<>(directIds);
        int depth = 1;
        while (!queue.isEmpty() && depth < 4) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                Long current = queue.poll();
                for (Long parentId : incomingAdjacency.getOrDefault(current, Collections.emptyList())) {
                    if (visited.add(parentId)) {
                        symbols.findById(parentId).ifPresent(transitiveDependents::add);
                        queue.add(parentId);
                    }
                }
            }
            depth++;
        }

        // Count affected findings
        List<FindingEntity> runFindings = findings.findByRunId(target.getRunId());
        Set<String> affectedQNames = new HashSet<>();
        affectedQNames.add(target.getQualifiedName());
        directDependents.forEach(d -> affectedQNames.add(d.getQualifiedName()));
        transitiveDependents.forEach(t -> affectedQNames.add(t.getQualifiedName()));

        int affectedFindings = (int) runFindings.stream()
                .filter(f -> f.getSymbolQualifiedName() != null && affectedQNames.contains(f.getSymbolQualifiedName()))
                .count();

        int totalAffected = directDependents.size() + transitiveDependents.size();
        String risk = (totalAffected > 5 || affectedFindings > 3) ? "HIGH"
                : (totalAffected > 1 || affectedFindings > 0) ? "MEDIUM" : "LOW";

        return new SymbolImpactResponse(
                symbolId,
                target.getQualifiedName(),
                "",
                directDependents.size(),
                transitiveDependents.size(),
                directDependents.stream().map(this::toSimpleNode).toList(),
                transitiveDependents.stream().map(this::toSimpleNode).toList(),
                affectedFindings,
                risk);
    }

    public HotspotListResponse getHotspots(Long analysisId) {
        AnalysisRunEntity run = runs.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis run not found: " + analysisId));

        List<SymbolEntity> allSymbols = symbols.findByRunId(analysisId);
        List<RelationshipEntity> allEdges = relationships.findByRunId(analysisId);
        List<FindingEntity> allFindings = findings.findByRunId(analysisId);

        Map<String, List<FindingEntity>> findingsBySymbol = new HashMap<>();
        for (FindingEntity f : allFindings) {
            if (f.getSymbolQualifiedName() != null) {
                findingsBySymbol.computeIfAbsent(f.getSymbolQualifiedName(), k -> new ArrayList<>()).add(f);
            }
        }

        Map<Long, Integer> inDegreeMap = new HashMap<>();
        Map<Long, Integer> outDegreeMap = new HashMap<>();
        for (RelationshipEntity rel : allEdges) {
            outDegreeMap.merge(rel.getSourceSymbolId(), 1, Integer::sum);
            if (rel.getTargetSymbolId() != null) {
                inDegreeMap.merge(rel.getTargetSymbolId(), 1, Integer::sum);
            }
        }

        List<HotspotItem> items = new ArrayList<>();
        for (SymbolEntity s : allSymbols) {
            List<FindingEntity> symFindings = findingsBySymbol.getOrDefault(s.getQualifiedName(), Collections.emptyList());
            int highCount = (int) symFindings.stream()
                    .filter(f -> f.getSeverity() == Severity.HIGH || f.getSeverity() == Severity.CRITICAL)
                    .count();
            int inDegree = inDegreeMap.getOrDefault(s.getId(), 0);
            int outDegree = outDegreeMap.getOrDefault(s.getId(), 0);
            int lines = s.getSpan().lineCount();
            double score = HotspotScore.calculate(symFindings.size(), highCount, inDegree, lines);

            items.add(new HotspotItem(
                    s.getId(),
                    s.getName(),
                    s.getQualifiedName(),
                    "",
                    lines,
                    symFindings.size(),
                    highCount,
                    inDegree,
                    outDegree,
                    score));
        }

        items.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<HotspotItem> topHotspots = items.stream().limit(50).toList();

        return new HotspotListResponse(
                analysisId,
                topHotspots,
                "score = (findings * 10.0) + (highSeverity * 20.0) + (inDegree * 5.0) + (lineCount * 0.05)");
    }

    private void traverseAround(Long focusId, int maxDepth, int nodeCap, List<RelationshipEntity> edges, Set<Long> out) {
        out.add(focusId);
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(focusId);
        int depth = 0;

        Map<Long, List<Long>> neighbors = new HashMap<>();
        for (RelationshipEntity e : edges) {
            neighbors.computeIfAbsent(e.getSourceSymbolId(), k -> new ArrayList<>()).add(e.getTargetSymbolId());
            if (e.getTargetSymbolId() != null) {
                neighbors.computeIfAbsent(e.getTargetSymbolId(), k -> new ArrayList<>()).add(e.getSourceSymbolId());
            }
        }

        while (!queue.isEmpty() && depth < maxDepth && out.size() < nodeCap) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                Long curr = queue.poll();
                for (Long n : neighbors.getOrDefault(curr, Collections.emptyList())) {
                    if (n != null && out.add(n)) {
                        queue.add(n);
                        if (out.size() >= nodeCap) {
                            return;
                        }
                    }
                }
            }
            depth++;
        }
    }

    private GraphNode toGraphNode(
            SymbolEntity s,
            Map<String, List<FindingEntity>> findingsBySymbol,
            Map<Long, Integer> inDegreeMap,
            Map<Long, Integer> outDegreeMap) {
        List<FindingEntity> symFindings = findingsBySymbol.getOrDefault(s.getQualifiedName(), Collections.emptyList());
        int highCount = (int) symFindings.stream()
                .filter(f -> f.getSeverity() == Severity.HIGH || f.getSeverity() == Severity.CRITICAL)
                .count();
        int inDegree = inDegreeMap.getOrDefault(s.getId(), 0);
        int outDegree = outDegreeMap.getOrDefault(s.getId(), 0);
        int lines = s.getSpan().lineCount();
        double hotspot = HotspotScore.calculate(symFindings.size(), highCount, inDegree, lines);

        return new GraphNode(
                String.valueOf(s.getId()),
                s.getName(),
                s.getQualifiedName(),
                s.getKind(),
                "",
                lines,
                symFindings.size(),
                hotspot,
                false,
                extractPackage(s.getQualifiedName()));
    }

    private GraphNode toSimpleNode(SymbolEntity s) {
        return new GraphNode(
                String.valueOf(s.getId()),
                s.getName(),
                s.getQualifiedName(),
                s.getKind(),
                "",
                s.getSpan().lineCount(),
                0, 0.0, false,
                extractPackage(s.getQualifiedName()));
    }

    private String extractPackage(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "(root)";
    }
}
