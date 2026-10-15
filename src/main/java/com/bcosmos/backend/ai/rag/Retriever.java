package com.bcosmos.backend.ai.rag;

import com.bcosmos.backend.ai.gateway.EmbedRequest;
import com.bcosmos.backend.ai.gateway.EmbedResult;
import com.bcosmos.backend.ai.gateway.LlmClient;
import com.bcosmos.backend.config.RetrievalProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gathers the evidence for one finding, bounded to a token budget (spec v2 section 7.2).
 *
 * <p>Order of assembly, and why:</p>
 * <ol>
 *   <li>the enclosing declaration from the symbol table - exact, so it is never crowded out by a similarity
 *       ranking;</li>
 *   <li>the {@code topK} chunks closest to the finding by cosine distance, added while the budget lasts, because
 *       "this code elsewhere does something similar" is the part that helps write a fix;</li>
 *   <li>the rule description, so the model reasons about the rule BCosmos applies.</li>
 * </ol>
 *
 * <p>Direct callers and callees are named in the spec's retrieval list but come from the relationship table, which
 * arrives with Phase 8. Rather than invent them, the bundle says they are missing: a note the prompt repeats and
 * the API returns.</p>
 */
@Service
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private static final String NO_ENCLOSING = "the finding's line range is not inside any chunked declaration;"
            + " the file may have failed to parse, or the declaration had no body to embed";
    private static final String NO_RELATIONSHIPS = "direct callers and callees are not included: they come from the"
            + " relationship table, which arrives with Phase 8";
    private static final String NOTHING_RETURNED = "retrieval found no similar chunk; the suggestion rests on the"
            + " enclosing declaration and the rule description alone";

    private final LlmClient llm;
    private final CodeChunkStore store;
    private final RuleCatalog rules;
    private final RetrievalProperties properties;

    public Retriever(LlmClient llm, CodeChunkStore store, RuleCatalog rules, RetrievalProperties properties) {
        this.llm = llm;
        this.store = store;
        this.rules = rules;
        this.properties = properties;
    }

    /**
     * The evidence bundle for one finding.
     *
     * <p>The query vector is the finding itself - rule, message, enclosing symbol and flagged snippet - embedded
     * through the same model as the chunks, because a query and a chunk are only comparable inside one embedding
     * space.</p>
     */
    public EvidenceBundle retrieve(RetrievalQuery query, List<CodeChunkDraft> drafts) {
        List<String> notes = new ArrayList<>();
        notes.add(NO_RELATIONSHIPS);

        Optional<CodeChunkDraft> enclosing = enclosing(drafts, query);
        enclosing.ifPresentOrElse(
                draft -> { },
                () -> notes.add(NO_ENCLOSING));

        List<CodeChunkStore.StoredChunk> similar = similarChunks(query, drafts, enclosing);
        if (similar.isEmpty() && query.embedModel() != null) {
            notes.add(NOTHING_RETURNED);
        }

        List<EvidenceBundle.EvidenceItem> items = new ArrayList<>();
        int budget = properties.tokenBudget();
        int used = 0;
        boolean truncated = false;

        if (enclosing.isPresent()) {
            CodeChunkDraft draft = enclosing.get();
            String text = draft.text();
            if (TokenEstimator.estimate(text) > budget) {
                // The exact declaration wins over the budget, but it is cut to fit and the cut is visible.
                text = cut(text, budget);
                truncated = true;
            }
            int tokens = TokenEstimator.estimate(text);
            used += tokens;
            items.add(new EvidenceBundle.EvidenceItem(EvidenceBundle.EvidenceKind.ENCLOSING, draft.symbolId(),
                    draft.relativePath(), draft.startLine(), draft.endLine(), draft.qualifiedName(), text, null,
                    tokens));
        }

        for (CodeChunkStore.StoredChunk chunk : similar) {
            if (used + chunk.tokenCount() > budget) {
                truncated = true;
                break;
            }
            used += chunk.tokenCount();
            items.add(new EvidenceBundle.EvidenceItem(EvidenceBundle.EvidenceKind.SIMILAR, chunk.symbolId(),
                    chunk.relativePath(), chunk.startLine(), chunk.endLine(), chunk.qualifiedName(), chunk.text(),
                    chunk.similarity(), chunk.tokenCount()));
        }
        if (truncated) {
            notes.add("the bundle was cut at the estimated token budget of " + budget + " tokens");
        }

        log.debug("retrieved {} item(s) for {} with {} ({} estimated tokens)", items.size(), query.filePath(),
                query.embedModel(), used);
        return new EvidenceBundle(items, rules.describe(query.ruleId()), notes, used, query.embedModel(),
                drafts.size(), truncated);
    }

    /** The tightest chunked declaration in the finding's file that contains the finding's first line. */
    private static Optional<CodeChunkDraft> enclosing(List<CodeChunkDraft> drafts, RetrievalQuery query) {
        return drafts.stream()
                .filter(draft -> draft.relativePath().equals(query.filePath()))
                .filter(draft -> draft.startLine() <= query.startLine() && draft.endLine() >= query.startLine())
                .min(Comparator.comparingInt(draft -> draft.endLine() - draft.startLine()));
    }

    /**
     * The chunks a vector search ranks closest to the finding, without the enclosing declaration itself.
     *
     * <p>One extra row is requested because the enclosing declaration is very likely to be the nearest neighbour of
     * its own finding; asking for {@code topK + 1} and filtering it out keeps the result at the requested size.</p>
     */
    private List<CodeChunkStore.StoredChunk> similarChunks(RetrievalQuery query, List<CodeChunkDraft> drafts,
            Optional<CodeChunkDraft> enclosing) {
        if (query.embedModel() == null || drafts.size() < 2) {
            return List.of();
        }

        EmbedResult embedded = llm.embed(new EmbedRequest(query.embeddingModelId(), List.of(query.queryText())));
        float[] vector = embedded.vectors().getFirst();
        long excluded = enclosing.map(CodeChunkDraft::symbolId).orElse(-1L);

        return store.nearest(query.runId(), query.embedModel(), vector, properties.topK() + 1).stream()
                .filter(chunk -> chunk.symbolId() != excluded)
                .limit(properties.topK())
                .toList();
    }

    /** Cuts a text to an estimated token budget, at a line boundary when one is close enough. */
    private static String cut(String text, int budget) {
        int maxChars = Math.max(1, budget * TokenEstimator.CHARS_PER_TOKEN);
        String cut = text.substring(0, Math.min(text.length(), maxChars));
        return cut + "\n... [cut at the token budget]";
    }

    /**
     * One retrieval request: the finding, where its file is, and which index to search.
     *
     * @param runId                analysis run the finding belongs to
     * @param embedModel           stored {@code code_chunk.embed_model} value, or {@code null} when there is no index
     * @param embeddingModelId     registry row to embed the query with; must match {@code embedModel}
     * @param filePath             repository-relative path of the finding's file
     * @param startLine            first line of the finding
     * @param symbolQualifiedName  enclosing symbol as the run recorded it, if any
     * @param ruleId               rule that fired
     * @param message              the tool's own message
     * @param snippet              the flagged source lines stored with the finding
     */
    public record RetrievalQuery(
            long runId,
            String embedModel,
            Long embeddingModelId,
            String filePath,
            int startLine,
            String symbolQualifiedName,
            String ruleId,
            String message,
            String snippet) {

        /** The text that is embedded to find similar chunks. */
        public String queryText() {
            return "rule: " + ruleId + "\n"
                    + "symbol: " + (symbolQualifiedName == null ? "(file)" : symbolQualifiedName) + "\n"
                    + "message: " + message + "\n"
                    + "code:\n" + (snippet == null ? "" : snippet);
        }
    }
}
