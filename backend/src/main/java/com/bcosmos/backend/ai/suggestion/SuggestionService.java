package com.bcosmos.backend.ai.suggestion;

import com.bcosmos.backend.ai.explanation.FindingNotFoundException;
import com.bcosmos.backend.ai.gateway.ChatMessage;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.PromptTemplate;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.StructuredRequest;
import com.bcosmos.backend.ai.gateway.StructuredResult;
import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.ai.rag.EmbeddingService;
import com.bcosmos.backend.ai.rag.Retriever;
import com.bcosmos.backend.persistence.FileContentEntity;
import com.bcosmos.backend.persistence.FileContentJpaRepository;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.SuggestionEntity;
import com.bcosmos.backend.persistence.SuggestionJpaRepository;
import com.bcosmos.backend.persistence.SuggestionStatus;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.quality.Fingerprint;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The Phase 3 flow of spec v2 section 7.3, in order: finding, retrieve evidence, build the prompt, ask one model
 * for schema-validated JSON, verify its citations, apply its diff in memory, re-run the rule analysis on the
 * patched file, store the answer with its verdict.
 *
 * <p>Two properties matter and are easy to lose:</p>
 * <ul>
 *   <li><b>No database transaction is held across a provider call.</b> A model can take tens of seconds; a
 *       transaction open for that long would hold a connection and a snapshot for no reason.</li>
 *   <li><b>Nothing is written to the repository.</b> The patch exists in memory, its verdict is stored as data, and
 *       a human decides what to do with it (spec v2 section 1.3).</li>
 * </ul>
 *
 * <p>With AI switched off, the gateway throws before any prompt is built, and the analysis side of the application
 * is untouched: that is the phase's "works with AI disabled" requirement.</p>
 */
@Service
public class SuggestionService {

    /** Name and version of the prompt in {@code src/main/resources/prompts}. */
    public static final String PROMPT_NAME = "suggest-fix";

    public static final String PROMPT_VERSION = "v1";

    private static final String NONE = "(none)";

    private final FindingJpaRepository findings;
    private final FileRecordJpaRepository files;
    private final FileContentJpaRepository contents;
    private final SuggestionJpaRepository suggestions;
    private final EmbeddingService embeddings;
    private final Retriever retriever;
    private final PatchValidator validator;
    private final LlmClient llm;
    private final PromptTemplateLoader prompts;
    private final ObjectMapper json;
    private final Clock clock;

    public SuggestionService(FindingJpaRepository findings, FileRecordJpaRepository files,
            FileContentJpaRepository contents, SuggestionJpaRepository suggestions, EmbeddingService embeddings,
            Retriever retriever, PatchValidator validator, LlmClient llm, PromptTemplateLoader prompts,
            ObjectMapper json, Clock clock) {
        this.findings = findings;
        this.files = files;
        this.contents = contents;
        this.suggestions = suggestions;
        this.embeddings = embeddings;
        this.retriever = retriever;
        this.validator = validator;
        this.llm = llm;
        this.prompts = prompts;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Asks one model for a fix suggestion for one stored finding.
     *
     * @param findingId        the stored finding
     * @param modelId          chat model registry id, or {@code null} for the default chat model
     * @param embeddingModelId embedding model registry id, or {@code null} for the default embedding model
     */
    public SuggestionResult suggest(long findingId, Long modelId, Long embeddingModelId) {
        FindingEntity finding = findings.findById(findingId)
                .orElseThrow(() -> new FindingNotFoundException(findingId));
        FileRecordEntity file = files.findById(finding.getFileId())
                .orElseThrow(() -> new FindingNotFoundException(findingId));
        FileContentEntity content = contents.findById(file.getId()).orElseThrow(() -> new SuggestionNotPossibleException(
                "the analysed source of " + file.getRelativePath() + " is not stored with this run, and a suggestion"
                        + " must be validated against it; re-run the analysis to store it"));

        // The finding's own file is indexed first, so even a bounded build gives this suggestion useful
        // neighbours (ADR 0044).
        EmbeddingService.IndexedRun indexed = embeddings.ensureIndex(finding.getRunId(), embeddingModelId,
                file.getRelativePath());
        EvidenceBundle bundle = retriever.retrieve(queryFor(finding, file, indexed), indexed.drafts());

        PromptTemplate template = prompts.load(PROMPT_NAME, PROMPT_VERSION);
        String prompt = template.render(promptValues(finding, file, bundle));
        StructuredRequest<FixSuggestion> request = StructuredRequest.of(modelId,
                List.of(ChatMessage.system(prompt)), FixSuggestion.class, template.schema(), template.id());

        StructuredResult<FixSuggestion> answer = llm.structured(request);
        boolean schemaOk = answer.validationErrors().isEmpty();
        ValidationVerdict verdict = validator.validate(finding.getFingerprint(), file.getRelativePath(),
                content.getText(), answer.value(), schemaOk, bundle, fileLineCounts(finding.getRunId()));

        SuggestionContext context = SuggestionContext.of(answer.model().label(), indexed, bundle);
        SuggestionEntity stored = suggestions.save(new SuggestionEntity(
                finding.getId(),
                answer.model().id(),
                template.id(),
                requestHash(prompt, answer.model().label()),
                writeJson(answer.value()),
                answer.value().proposedDiff(),
                writeJson(verdict),
                writeJson(context),
                answer.usage().latencyMs(),
                answer.usage().tokensIn(),
                answer.usage().tokensOut(),
                clock.instant()));
        return new SuggestionResult(stored, answer.value(), verdict, context, answer.model().label());
    }

    /** One stored suggestion, reopened with the answer, the verdict and the evidence it was given. */
    @Transactional(readOnly = true)
    public SuggestionResult get(long suggestionId) {
        SuggestionEntity suggestion = suggestions.findById(suggestionId)
                .orElseThrow(() -> new SuggestionNotFoundException(suggestionId));
        return read(suggestion);
    }

    /** The suggestions of one finding, oldest first: the review screen shows what was tried before. */
    @Transactional(readOnly = true)
    public Page<SuggestionResult> history(Long findingId, Pageable pageable) {
        return suggestions.findByFindingIdOrderByCreatedAtAsc(findingId, pageable).map(this::read);
    }

    /**
     * Records a human's decision on a suggestion.
     *
     * <p>The finding's triage state moves with it: accepting or rejecting a suggestion is the same decision the
     * Quality screen's triage column tracks, and two fields holding different answers would be a bug.</p>
     */
    @Transactional
    public SuggestionResult review(long suggestionId, SuggestionStatus decision) {
        SuggestionEntity suggestion = suggestions.findById(suggestionId)
                .orElseThrow(() -> new SuggestionNotFoundException(suggestionId));
        suggestion.review(decision, clock.instant());
        suggestions.save(suggestion);
        findings.findById(suggestion.getFindingId()).ifPresent(finding -> {
            finding.triage(triageOf(decision));
            findings.save(finding);
        });
        return read(suggestion);
    }

    private static TriageStatus triageOf(SuggestionStatus decision) {
        return switch (decision) {
            case ACCEPTED -> TriageStatus.ACCEPTED;
            case REJECTED -> TriageStatus.REJECTED;
            case DEFERRED -> TriageStatus.DEFERRED;
            case PROPOSED -> TriageStatus.OPEN;
        };
    }

    private Retriever.RetrievalQuery queryFor(FindingEntity finding, FileRecordEntity file,
            EmbeddingService.IndexedRun indexed) {
        Span span = finding.getSpan();
        return new Retriever.RetrievalQuery(
                finding.getRunId(),
                indexed.index().empty() ? null : indexed.index().embedModel(),
                indexed.index().model().id(),
                file.getRelativePath(),
                span.startLine(),
                finding.getSymbolQualifiedName(),
                finding.getRuleId(),
                finding.getMessage(),
                finding.getSnippet(),
                indexed.index().chunks(),
                indexed.drafts().size());
    }

    private Map<String, String> promptValues(FindingEntity finding, FileRecordEntity file, EvidenceBundle bundle) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("rule", finding.getRuleId());
        values.put("severity", finding.getSeverity().name());
        values.put("originalSeverity", finding.getOriginalSeverity());
        values.put("message", finding.getMessage());
        values.put("tool", finding.getTool());
        values.put("toolVersion", finding.getToolVersion());
        values.put("language", file.getLanguage());
        values.put("file", file.getRelativePath());
        values.put("startLine", Integer.toString(finding.getSpan().startLine()));
        values.put("endLine", Integer.toString(finding.getSpan().endLine()));
        values.put("symbol", finding.getSymbolQualifiedName() == null ? "(the file itself)"
                : finding.getSymbolQualifiedName());
        values.put("ruleDescription", bundle.ruleDescription());
        values.put("retrievalNotes", bundle.notes().isEmpty() ? NONE : String.join("\n", bundle.notes()));
        values.put("enclosingCode", bundle.enclosing().map(item -> item.text()).orElse(NONE));
        values.put("similarChunks", similarChunks(bundle));
        return values;
    }

    /** The similar chunks as one block, each labelled with the citation the model is allowed to reuse. */
    private static String similarChunks(EvidenceBundle bundle) {
        String block = bundle.items().stream()
                .filter(item -> item.kind() == EvidenceBundle.EvidenceKind.SIMILAR)
                .map(item -> "--- " + item.file() + " lines " + item.startLine() + "-" + item.endLine()
                        + " (" + item.qualifiedName()
                        + (item.similarity() == null ? "" : ", similarity " + String.format("%.3f", item.similarity()))
                        + ")\n" + item.text())
                .collect(Collectors.joining("\n\n"));
        return block.isEmpty() ? NONE : block;
    }

    private Map<String, Integer> fileLineCounts(long runId) {
        return files.findByRunId(runId).stream().collect(Collectors.toMap(FileRecordEntity::getRelativePath,
                FileRecordEntity::getLineCount, (first, second) -> first, LinkedHashMap::new));
    }

    /** Stable identity of the exact request, so the same prompt sent twice is recognizable. */
    private static String requestHash(String prompt, String modelLabel) {
        return Fingerprint.sha256Hex(modelLabel + "\n" + prompt);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("a suggestion could not be stored as JSON", failure);
        }
    }

    private SuggestionResult read(SuggestionEntity suggestion) {
        SuggestionContext context = readJson(suggestion.getRetrievalJson(), SuggestionContext.class);
        return new SuggestionResult(
                suggestion,
                readJson(suggestion.getResponseJson(), FixSuggestion.class),
                readJson(suggestion.getValidationJson(), ValidationVerdict.class),
                context,
                context.modelLabel());
    }

    private <T> T readJson(String payload, Class<T> type) {
        try {
            return json.readValue(payload, type);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("stored suggestion data could not be read back as "
                    + type.getSimpleName(), failure);
        }
    }

    /** A suggestion with everything a review screen needs, whether it was just produced or read back. */
    public record SuggestionResult(
            SuggestionEntity suggestion,
            FixSuggestion answer,
            ValidationVerdict verdict,
            SuggestionContext context,
            String modelLabel) {

        /** Ids and review state come from the entity; nothing else outside this service reads it. */
        public long id() {
            return suggestion.getId();
        }

        /** Small helper for callers that only need a path-to-finding mapping. */
        public static Function<SuggestionEntity, Long> findingId() {
            return SuggestionEntity::getFindingId;
        }
    }
}
