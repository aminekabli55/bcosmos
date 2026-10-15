package com.bcosmos.backend.ai.rag;

import com.bcosmos.backend.config.RetrievalProperties;
import com.bcosmos.core.model.SymbolKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns symbols into chunks, one per method or type (spec v2 section 7.2: "chunking by symbol ... no arbitrary
 * character splits").
 *
 * <p>Rules that matter:</p>
 * <ul>
 *   <li>The code comes from the stored revision of the run, never from the repository on disk: the file may have
 *       changed since, and a chunk must describe what was analysed.</li>
 *   <li>Whole lines are taken, and the exact line range is kept on the chunk, so a citation in a suggestion can be
 *       checked against the file record.</li>
 *   <li>Fields are skipped. A one-line field is not a unit of code anyone reasons about, and embedding thousands of
 *       them would spend money on noise. Types and callables are the units (spec 7.2).</li>
 *   <li>A declaration longer than the chunk limit is truncated with a visible marker instead of silently costing
 *       the whole context window.</li>
 * </ul>
 */
@Component
public class SymbolChunker {

    /** Kinds worth one chunk each: declarations with a body someone can fix. */
    private static final Set<SymbolKind> CHUNKED_KINDS = EnumSet.of(
            SymbolKind.CLASS,
            SymbolKind.INTERFACE,
            SymbolKind.ENUM,
            SymbolKind.RECORD,
            SymbolKind.ANNOTATION,
            SymbolKind.METHOD,
            SymbolKind.CONSTRUCTOR);

    private static final String HEADER_SEPARATOR = "--------";

    private final int maxChunkChars;

    public SymbolChunker(RetrievalProperties properties) {
        this.maxChunkChars = properties.maxChunkChars();
    }

    /** One chunk per chunk-worthy symbol, in the order the symbols were given. */
    public List<CodeChunkDraft> chunks(List<SymbolSource> sources) {
        List<CodeChunkDraft> chunks = new ArrayList<>();
        for (SymbolSource source : sources) {
            if (!CHUNKED_KINDS.contains(source.kind())) {
                continue;
            }
            String code = slice(source);
            if (code.isBlank()) {
                // An empty declaration (a marker interface, a stub) carries no code to retrieve; embedding it
                // would only add a vector that matches nothing.
                continue;
            }
            boolean truncated = code.length() > maxChunkChars;
            if (truncated) {
                code = code.substring(0, maxChunkChars) + "\n... [truncated at " + maxChunkChars + " characters]";
            }
            String text = header(source) + code;
            chunks.add(new CodeChunkDraft(source.symbolId(), source.fileId(), source.relativePath(),
                    source.qualifiedName(), source.span().startLine(), source.span().endLine(), text,
                    TokenEstimator.estimate(text), truncated));
        }
        return List.copyOf(chunks);
    }

    /**
     * The lines a chunk starts with.
     *
     * <p>It is a plain, language-neutral header rather than a comment: a {@code //} prefix would be wrong for the
     * second language (spec v2 section 3.1), and the chunk is not compiled — it is embedded and quoted.</p>
     */
    private static String header(SymbolSource source) {
        return "file: " + source.relativePath()
                + " lines " + source.span().startLine() + "-" + source.span().endLine() + "\n"
                + "symbol: " + source.qualifiedName() + " [" + source.kind() + "]\n"
                + HEADER_SEPARATOR + "\n";
    }

    /** The declaration's lines, or an empty string when the stored revision does not cover them. */
    private static String slice(SymbolSource source) {
        String[] lines = source.text().split("\n", -1);
        int first = source.span().startLine();
        int last = Math.min(source.span().endLine(), lines.length);
        if (first < 1 || last < first || first > lines.length) {
            return "";
        }
        StringBuilder code = new StringBuilder();
        for (int line = first; line <= last; line++) {
            if (!code.isEmpty()) {
                code.append('\n');
            }
            code.append(lines[line - 1]);
        }
        return code.toString();
    }
}
