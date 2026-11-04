package com.bcosmos.backend.web;

import com.bcosmos.backend.graph.GraphService;
import com.bcosmos.backend.web.dto.GraphResponses.GraphNode;
import com.bcosmos.backend.web.dto.GraphResponses.GraphResponse;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotListResponse;
import com.bcosmos.backend.web.dto.GraphResponses.SymbolImpactResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for Code Cosmos graph, impact analysis, and triage hotspots (Phase 8, spec section 8 & 9).
 */
@RestController
@RequestMapping("/api")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/analyses/{id}/graph")
    public GraphResponse getGraph(
            @PathVariable("id") Long analysisId,
            @RequestParam(name = "depth", required = false) Integer depth,
            @RequestParam(name = "maxNodes", required = false) Integer maxNodes,
            @RequestParam(name = "maxEdges", required = false) Integer maxEdges,
            @RequestParam(name = "packageFilter", required = false) String packageFilter,
            @RequestParam(name = "focusSymbolId", required = false) Long focusSymbolId) {
        return graphService.getGraph(analysisId, depth, maxNodes, maxEdges, packageFilter, focusSymbolId);
    }

    @GetMapping("/symbols/{id}/dependencies")
    public List<GraphNode> getDependencies(@PathVariable("id") Long symbolId) {
        return graphService.getDependencies(symbolId);
    }

    @GetMapping("/symbols/{id}/dependents")
    public List<GraphNode> getDependents(@PathVariable("id") Long symbolId) {
        return graphService.getDependents(symbolId);
    }

    @GetMapping("/symbols/{id}/impact")
    public SymbolImpactResponse getImpact(@PathVariable("id") Long symbolId) {
        return graphService.getImpact(symbolId);
    }

    @GetMapping("/analyses/{id}/hotspots")
    public HotspotListResponse getHotspots(@PathVariable("id") Long analysisId) {
        return graphService.getHotspots(analysisId);
    }
}
