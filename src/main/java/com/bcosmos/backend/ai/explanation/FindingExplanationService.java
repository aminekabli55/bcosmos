package com.bcosmos.backend.ai.explanation;

import com.bcosmos.backend.ai.gateway.ChatMessage;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.ai.gateway.PromptTemplate;
import com.bcosmos.backend.ai.gateway.PromptTemplateLoader;
import com.bcosmos.backend.ai.gateway.StructuredRequest;
import com.bcosmos.backend.ai.gateway.StructuredResult;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 2's minimal AI call: explain one stored finding with a chosen model (spec v2 section 8,
 * {@code POST /api/findings/{id}/explanations}).
 *
 * <p>It builds the prompt from the versioned template plus the finding's own stored evidence, and asks the gateway
 * for a schema-validated answer. Phase 3 replaces this endpoint with suggestions, which add retrieval and a
 * validated diff; the gateway underneath does not change.</p>
 *
 * <p>The service never reads the repository on disk: the snippet and the relative path came from the run, so an
 * explanation describes the revision that was analysed, and no absolute path ever reaches a prompt.</p>
 */
@Service
public class FindingExplanationService {

    /** Name and version of the prompt in {@code src/main/resources/prompts}. */
    public static final String PROMPT_NAME = "explain-finding";

    public static final String PROMPT_VERSION = "v1";

    private static final String NO_SYMBOL = "(the file itself)";
    private static final String NO_SNIPPET = "(the run stored no snippet for this finding)";

    private final FindingJpaRepository findings;
    private final FileRecordJpaRepository files;
    private final LlmClient llm;
    private final PromptTemplateLoader prompts;

    public FindingExplanationService(FindingJpaRepository findings, FileRecordJpaRepository files, LlmClient llm,
            PromptTemplateLoader prompts) {
        this.findings = findings;
        this.files = files;
        this.llm = llm;
        this.prompts = prompts;
    }

    /**
     * Asks one model to explain one finding.
     *
     * @param findingId the stored finding
     * @param modelId   registry id of the model, or {@code null} for the default model
     */
    @Transactional(readOnly = true)
    public FindingExplanationResult explain(long findingId, Long modelId) {
        FindingEntity finding = findings.findById(findingId).orElseThrow(() -> new FindingNotFoundException(findingId));
        FileRecordEntity file = files.findById(finding.getFileId())
                .orElseThrow(() -> new FindingNotFoundException(findingId));

        PromptTemplate template = prompts.load(PROMPT_NAME, PROMPT_VERSION);
        StructuredRequest<FindingExplanation> request = StructuredRequest.of(
                modelId,
                List.of(ChatMessage.system(template.render(promptValues(finding, file)))),
                FindingExplanation.class,
                template.schema(),
                template.id());

        StructuredResult<FindingExplanation> result = llm.structured(request);
        return new FindingExplanationResult(result.value(), result.model(), template.id(), result.usage(),
                result.validationErrors());
    }

    private static Map<String, String> promptValues(FindingEntity finding, FileRecordEntity file) {
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
        values.put("symbol", finding.getSymbolQualifiedName() == null ? NO_SYMBOL : finding.getSymbolQualifiedName());
        values.put("snippet", finding.getSnippet() == null ? NO_SNIPPET : finding.getSnippet());
        return values;
    }
}
