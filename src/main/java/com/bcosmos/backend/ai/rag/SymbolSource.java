package com.bcosmos.backend.ai.rag;

import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.SymbolKind;
import java.util.Objects;

/**
 * One symbol together with the source it was declared in, as the chunker needs it.
 *
 * <p>It exists so the chunker is a pure function over data: no JPA entity, no repository, no filesystem. The
 * loader resolves symbols, file records and the stored revision, and hands the chunker this.</p>
 *
 * @param symbolId       row id of the symbol, stored on the chunk so a citation can be traced back
 * @param fileId         row id of the file record
 * @param kind           symbol kind; only declarations worth embedding become chunks
 * @param qualifiedName  fully qualified symbol name, used in the chunk header and for citations
 * @param relativePath   repository-relative path; never absolute (spec v2 section 10)
 * @param span           line range of the declaration inside the file
 * @param text           the stored source of the analysed revision
 */
public record SymbolSource(
        long symbolId,
        long fileId,
        SymbolKind kind,
        String qualifiedName,
        String relativePath,
        Span span,
        String text) {

    public SymbolSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(text, "text");
        if (relativePath.startsWith("/") || relativePath.contains(":\\")) {
            throw new IllegalArgumentException("relativePath must be repository-relative, never absolute");
        }
    }
}
