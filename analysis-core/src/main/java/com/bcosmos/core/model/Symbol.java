package com.bcosmos.core.model;

import java.util.Objects;

/**
 * One symbol extracted from a parsed file (class, interface, enum, record, annotation, method, constructor, field).
 *
 * @param kind              symbol kind
 * @param name              simple name, e.g. {@code findById}
 * @param qualifiedName     package + enclosing types + name, e.g. {@code com.acme.UserService.findById}
 * @param parentQualifiedName qualified name of the enclosing symbol, or {@code null} for a top-level type
 * @param span              position in the file
 * @param signatureHash     sha256 of the normalized declaration text (stable identity of the declaration)
 */
public record Symbol(
        SymbolKind kind,
        String name,
        String qualifiedName,
        String parentQualifiedName,
        Span span,
        String signatureHash) {

    public Symbol {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(signatureHash, "signatureHash");
    }

    /** True when this symbol encloses the given line, i.e. it is a candidate for "the enclosing symbol". */
    public boolean encloses(int line) {
        return span.containsLine(line);
    }

    /** How many lines this symbol spans; used to pick the tightest enclosing symbol for a finding. */
    public int lineCount() {
        return span.lineCount();
    }
}
