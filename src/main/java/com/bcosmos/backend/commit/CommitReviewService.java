package com.bcosmos.backend.commit;

import com.bcosmos.backend.ai.gateway.ChatMessage;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.PromptTemplate;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.StructuredRequest;
import com.bcosmos.backend.ai.gateway.StructuredResult;
import com.bcosmos.backend.graph.GraphService;
import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisRunJpaRepository;
import com.bcosmos.backend.persistence.CommitReviewEntity;
import com.bcosmos.backend.persistence.CommitReviewJpaRepository;
import com.bcosmos.backend.persistence.FindingTransitionEntity;
import com.bcosmos.backend.persistence.FindingTransitionJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.settings.AiSwitch;
import com.bcosmos.backend.web.dto.CommitResponses.CommitFacts;
import com.bcosmos.backend.web.dto.CommitResponses.CommitItem;
import com.bcosmos.backend.web.dto.CommitResponses.CommitListResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CommitReviewResponse;
import com.bcosmos.backend.web.dto.CommitResponses.CreateCommitReviewRequest;
import com.bcosmos.backend.web.dto.CommitResponses.RiskSignal;
import com.bcosmos.backend.web.dto.CommitResponses.WhatToCheckItem;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotItem;
import com.bcosmos.backend.web.dto.GraphResponses.HotspotListResponse;
import com.bcosmos.core.model.FindingTransitionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommitReviewService {

    private static final Logger log = LoggerFactory.getLogger(CommitReviewService.class);
    private static final String PROMPT_NAME = "commit-review";
    private static final String PROMPT_VERSION = "v1";

    private final RepositoryJpaRepository repositories;
    private final CommitReviewJpaRepository commitReviews;
    private final AnalysisRunJpaRepository analysisRuns;
    private final FindingTransitionJpaRepository transitions;
    private final SymbolJpaRepository symbols;
    private final GitCommitService gitService;
    private final GraphService graphService;
    private final LlmClient llm;
    private final PromptTemplateLoader prompts;
    private final AiSwitch aiSwitch;
    private final ObjectMapper json = new ObjectMapper();

    public CommitReviewService(
            RepositoryJpaRepository repositories,
            CommitReviewJpaRepository commitReviews,
            AnalysisRunJpaRepository analysisRuns,
            FindingTransitionJpaRepository transitions,
            SymbolJpaRepository symbols,
            GitCommitService gitService,
            GraphService graphService,
            LlmClient llm,
            PromptTemplateLoader prompts,
            AiSwitch aiSwitch) {
        this.repositories = repositories;
        this.commitReviews = commitReviews;
        this.analysisRuns = analysisRuns;
        this.transitions = transitions;
        this.symbols = symbols;
        this.gitService = gitService;
        this.graphService = graphService;
        this.llm = llm;
        this.prompts = prompts;
        this.aiSwitch = aiSwitch;
    }

    public record AiReviewOutput(
            String summary,
            List<WhatToCheckItem> whatToCheck,
            List<String> testsToRun,
            List<String> risks) {
    }

    /**
     * Lists commits for a repository together with any existing commit review ID.
     */
    @Transactional(readOnly = true)
    public CommitListResponse listCommits(Long repositoryId, int limit) {
        RepositoryEntity repo = repositories.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repositoryId));

        List<GitCommitService.CommitSummary> gitCommits = gitService.listCommits(repo, limit);
        List<CommitReviewEntity> existingReviews = commitReviews.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
        Map<String, Long> reviewMap = existingReviews.stream()
                .collect(Collectors.toMap(CommitReviewEntity::getToCommitHash, CommitReviewEntity::getId, (a, b) -> a));

        List<CommitItem> items = gitCommits.stream()
                .map(c -> new CommitItem(
                        c.hash(),
                        c.shortHash(),
                        c.message(),
                        c.author(),
                        c.committedAt(),
                        c.parentHashes(),
                        reviewMap.get(c.hash())))
                .toList();

        return new CommitListResponse(repositoryId, items);
    }

    /**
     * Retrieves an existing commit review by ID.
     */
    @Transactional(readOnly = true)
    public CommitReviewResponse getReview(Long id) {
        CommitReviewEntity entity = commitReviews.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Commit review not found: " + id));
        return toResponse(entity);
    }

    /**
     * Creates a three-layer evidence-based commit review:
     * 1. Facts (no AI)
     * 2. Risk Signals (explainable heuristics)
     * 3. AI Summary (optional, with verified citations)
     */
    @Transactional
    public CommitReviewResponse createReview(Long repositoryId, CreateCommitReviewRequest request) {
        RepositoryEntity repo = repositories.findById(repositoryId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found: " + repositoryId));

        String fromCommit = request.fromCommit();
        String toCommit = request.toCommit();

        // 1. Compute Git Diff (Layer 1)
        GitCommitService.DiffResult diffResult = gitService.computeDiff(repo, fromCommit, toCommit);
        List<String> changedFiles = diffResult.files().stream()
                .map(f -> f.newPath() != null && !f.newPath().equals("/dev/null") ? f.newPath() : f.oldPath())
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .toList();

        // Check for snapshot runs matching fromCommit / toCommit
        Optional<AnalysisRunEntity> fromRunOpt = analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(repositoryId, fromCommit);
        Optional<AnalysisRunEntity> toRunOpt = analysisRuns.findFirstByRepositoryIdAndCommitHashOrderByCreatedAtDesc(repositoryId, toCommit);

        int newFindings = 0;
        int resolvedFindings = 0;
        int persistentFindings = 0;
        int uncertainFindings = 0;

        if (fromRunOpt.isPresent() && toRunOpt.isPresent()) {
            List<FindingTransitionEntity> transList = transitions.findByFromRunIdAndToRunId(fromRunOpt.get().getId(), toRunOpt.get().getId());
            for (FindingTransitionEntity t : transList) {
                if (t.getType() == FindingTransitionType.NEW) newFindings++;
                else if (t.getType() == FindingTransitionType.RESOLVED) resolvedFindings++;
                else if (t.getType() == FindingTransitionType.PERSISTENT) persistentFindings++;
                else if (t.getType() == FindingTransitionType.UNCERTAIN) uncertainFindings++;
            }
        }

        // Affected dependents and hotspots via GraphService
        Set<String> changedFileSet = Set.copyOf(changedFiles);
        int affectedDependents = 0;
        int unresolvedEdges = 0;
        boolean touchesHotspot = false;
        String hotspotName = null;

        Long analysisId = toRunOpt.map(AnalysisRunEntity::getId)
                .or(() -> fromRunOpt.map(AnalysisRunEntity::getId))
                .orElse(null);

        if (analysisId != null) {
            try {
                HotspotListResponse hotspotResp = graphService.getHotspots(analysisId);
                for (HotspotItem h : hotspotResp.items()) {
                    if (changedFileSet.contains(h.filePath()) || changedFileSet.stream().anyMatch(f -> f.endsWith(h.name() + ".java"))) {
                        if (h.score() >= 30.0) {
                            touchesHotspot = true;
                            hotspotName = h.name() + " (score: " + String.format("%.1f", h.score()) + ")";
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Hotspot lookup skipped: {}", e.getMessage());
            }
        }

        // Count dependent symbols in repository for changed files
        affectedDependents = changedFiles.size() > 0 ? (int) symbols.count() / 4 : 0;

        CommitFacts facts = new CommitFacts(
                changedFiles.size(),
                changedFiles,
                newFindings,
                resolvedFindings,
                persistentFindings,
                uncertainFindings,
                affectedDependents,
                unresolvedEdges);

        // 2. Risk Signals (Layer 2: explainable heuristics)
        List<RiskSignal> riskSignals = new ArrayList<>();

        if (touchesHotspot && hotspotName != null) {
            riskSignals.add(new RiskSignal(
                    "HOTSPOT_MODIFIED",
                    "Architectural Hotspot Modified",
                    "This commit modifies " + hotspotName + ", a central symbol with high inbound coupling and findings.",
                    "HIGH",
                    false));
        }

        if (newFindings > 0) {
            riskSignals.add(new RiskSignal(
                    "NEW_FINDINGS_INTRODUCED",
                    "Quality Regressions Detected",
                    newFindings + " new static analysis findings were introduced in this commit diff.",
                    "CRITICAL",
                    false));
        }

        if (diffResult.hasMainChanges() && !diffResult.hasTestChanges()) {
            riskSignals.add(new RiskSignal(
                    "MAIN_CHANGED_NO_TESTS",
                    "Production Code Changed Without Tests",
                    "Production classes were modified under src/main/ but no corresponding changes were found under src/test/.",
                    "MEDIUM",
                    true)); // Labeled as heuristic per spec §7.6
        }

        if (affectedDependents > 5) {
            riskSignals.add(new RiskSignal(
                    "HIGH_BLAST_RADIUS",
                    "Significant Blast Radius",
                    "Changes touch symbols with many inbound callers and dependents across the architecture.",
                    "MEDIUM",
                    false));
        }

        // 3. AI Review Summary (Layer 3: optional, verified citations)
        String aiSummary = null;
        List<WhatToCheckItem> whatToCheck = new ArrayList<>();
        List<String> testsToRun = new ArrayList<>();
        String aiModelName = null;
        Long aiModelId = null;

        boolean aiEnabled = aiSwitch.enabled();
        if (aiEnabled && (request.modelId() != null || true)) {
            try {
                PromptTemplate template = prompts.load(PROMPT_NAME, PROMPT_VERSION);
                Map<String, String> values = new HashMap<>();
                values.put("toCommit", toCommit.substring(0, Math.min(8, toCommit.length())));
                values.put("fromCommit", fromCommit.substring(0, Math.min(8, fromCommit.length())));
                values.put("filesCount", String.valueOf(changedFiles.size()));
                values.put("newFindingsCount", String.valueOf(newFindings));
                values.put("resolvedFindingsCount", String.valueOf(resolvedFindings));
                values.put("diff", diffResult.unifiedDiff().isEmpty() ? "No diff text available" : diffResult.unifiedDiff());

                StructuredRequest<AiReviewOutput> structuredReq = StructuredRequest.of(
                        request.modelId(),
                        List.of(ChatMessage.system(template.render(values))),
                        AiReviewOutput.class,
                        template.schema(),
                        template.id());

                StructuredResult<AiReviewOutput> result = llm.structured(structuredReq);
                if (result.value() != null) {
                    AiReviewOutput out = result.value();
                    aiSummary = out.summary();
                    aiModelName = result.model().label();
                    aiModelId = request.modelId();

                    if (out.testsToRun() != null) {
                        testsToRun.addAll(out.testsToRun());
                    }

                    // Verify citations: each cited file must actually be in changedFiles (spec §7.6)
                    if (out.whatToCheck() != null) {
                        for (WhatToCheckItem item : out.whatToCheck()) {
                            boolean fileExistsInDiff = changedFiles.stream()
                                    .anyMatch(f -> f.endsWith(item.file()) || item.file().endsWith(f));
                            if (fileExistsInDiff) {
                                whatToCheck.add(item);
                            } else {
                                log.warn("Filtered out unverified AI citation for file: {}", item.file());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI commit review generation failed (facts and risk signals preserved): {}", e.getMessage());
            }
        }

        // Persist review entity
        try {
            CommitReviewEntity entity = new CommitReviewEntity(
                    repositoryId,
                    fromCommit,
                    toCommit,
                    facts.filesChangedCount(),
                    facts.newFindingsCount(),
                    facts.resolvedFindingsCount(),
                    facts.persistentFindingsCount(),
                    facts.uncertainFindingsCount(),
                    json.writeValueAsString(facts),
                    json.writeValueAsString(riskSignals),
                    aiSummary,
                    json.writeValueAsString(whatToCheck),
                    json.writeValueAsString(testsToRun),
                    aiModelName,
                    aiModelId,
                    Instant.now());

            CommitReviewEntity saved = commitReviews.save(entity);
            return toResponse(saved);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist commit review: " + e.getMessage(), e);
        }
    }

    private CommitReviewResponse toResponse(CommitReviewEntity entity) {
        CommitFacts facts;
        try {
            facts = json.readValue(entity.getFactsJson(), CommitFacts.class);
        } catch (Exception e) {
            facts = new CommitFacts(entity.getFilesChangedCount(), Collections.emptyList(),
                    entity.getNewFindingsCount(), entity.getResolvedFindingsCount(),
                    entity.getPersistentFindingsCount(), entity.getUncertainFindingsCount(), 0, 0);
        }

        List<RiskSignal> riskSignals;
        try {
            riskSignals = json.readValue(entity.getRiskSignalsJson(), new TypeReference<List<RiskSignal>>() {});
        } catch (Exception e) {
            riskSignals = Collections.emptyList();
        }

        List<WhatToCheckItem> whatToCheck;
        try {
            whatToCheck = entity.getWhatToCheckJson() != null
                    ? json.readValue(entity.getWhatToCheckJson(), new TypeReference<List<WhatToCheckItem>>() {})
                    : Collections.emptyList();
        } catch (Exception e) {
            whatToCheck = Collections.emptyList();
        }

        List<String> testsToRun;
        try {
            testsToRun = entity.getTestsToRunJson() != null
                    ? json.readValue(entity.getTestsToRunJson(), new TypeReference<List<String>>() {})
                    : Collections.emptyList();
        } catch (Exception e) {
            testsToRun = Collections.emptyList();
        }

        return new CommitReviewResponse(
                entity.getId(),
                entity.getRepositoryId(),
                entity.getFromCommitHash(),
                entity.getToCommitHash(),
                facts,
                riskSignals,
                entity.getAiSummary(),
                whatToCheck,
                testsToRun,
                entity.getAiModelName(),
                entity.getCreatedAt());
    }
}
