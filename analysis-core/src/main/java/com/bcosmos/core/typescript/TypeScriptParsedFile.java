package com.bcosmos.core.typescript;

import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.model.Symbol;
import org.treesitter.TSTree;

import java.util.List;
import java.util.Objects;

/**
 * TypeScript view of a parsed file: the Tree-sitter AST plus symbols extracted from it.
 * Supports Level 1 and 2 (spec v2 section 3.1).
 */
public final class TypeScriptParsedFile implements ParsedFile {

    private final String filePath;
    private final String source;
    private final TSTree tree;
    private final ParseStatus parseStatus;
    private final List<ParseDiagnostic> diagnostics;
    private final List<Symbol> symbols;

    TypeScriptParsedFile(
            String filePath,
            String source,
            TSTree tree,
            ParseStatus parseStatus,
            List<ParseDiagnostic> diagnostics,
            List<Symbol> symbols) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.source = Objects.requireNonNull(source, "source");
        this.tree = tree;
        this.parseStatus = Objects.requireNonNull(parseStatus, "parseStatus");
        this.diagnostics = List.copyOf(diagnostics);
        this.symbols = List.copyOf(symbols);
    }

    @Override
    public String languageId() {
        return TypeScriptAdapter.LANGUAGE_ID;
    }

    @Override
    public String filePath() {
        return filePath;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public ParseStatus parseStatus() {
        return parseStatus;
    }

    @Override
    public List<ParseDiagnostic> diagnostics() {
        return diagnostics;
    }

    public List<Symbol> symbols() {
        return symbols;
    }

    public TSTree tree() {
        return tree;
    }
}
