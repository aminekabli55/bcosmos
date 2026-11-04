package com.bcosmos.core.java;

import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.model.Symbol;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Java view of a parsed file: the JavaParser compilation unit plus the symbols extracted from it.
 *
 * <p>Java targets support level 3 (spec v2 section 3.1), so parsing already extracts symbols and
 * {@link JavaAdapter#extractSymbols(JavaParsedFile)} only returns them.</p>
 */
public final class JavaParsedFile implements ParsedFile {

    private final String filePath;
    private final String source;
    private final CompilationUnit ast;
    private final ParseStatus parseStatus;
    private final List<ParseDiagnostic> diagnostics;
    private final List<Symbol> symbols;

    JavaParsedFile(
            String filePath,
            String source,
            CompilationUnit ast,
            ParseStatus parseStatus,
            List<ParseDiagnostic> diagnostics,
            List<Symbol> symbols) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.source = Objects.requireNonNull(source, "source");
        this.ast = ast;
        this.parseStatus = Objects.requireNonNull(parseStatus, "parseStatus");
        this.diagnostics = List.copyOf(diagnostics);
        this.symbols = List.copyOf(symbols);
    }

    @Override
    public String languageId() {
        return JavaAdapter.LANGUAGE_ID;
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

    /** Symbols extracted while parsing; empty when the file did not parse. */
    public List<Symbol> symbols() {
        return symbols;
    }

    /** The syntax tree, empty when parsing produced no compilation unit at all. */
    public Optional<CompilationUnit> ast() {
        return Optional.ofNullable(ast);
    }
}
