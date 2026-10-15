package com.bcosmos.core.language;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.model.Symbol;
import java.nio.file.Path;
import java.util.List;

/** Everything language-specific lives behind this interface, so the backend, the worker, the API and the database stay language-independent (ADR 0012, spec v2 section 3.1). * * <p>Adapters never touch the filesystem: the caller reads the source with its own limits and passes the text in, * and every path handed to an adapter is repository-relative. Analyzed code is untrusted input and is only ever * parsed or analyzed, never executed (spec v2 section 0 rule 4, section 10).</p> * * <p>Scope note: the interface sketch in spec v2 section 3.1 also lists {@code extractRelationships} and * {@code checkPatch}. They are intentionally absent here — the graph phase (8) and the suggestion phase (3) * implement them together with their first real (and tested) implementation, instead of carrying a method that returns nothing. See the report for this phase and {@code docs/decisions/ADR-0015}.</p> * * @param <T> the adapter's own parsed-file type (for Java, a JavaParser compilation unit) */ public interface LanguageAdapter<T extends ParsedFile> {

    /** The stable language id recorded with every file, symbol and finding, e.g. {@code java}. */    /** Stable language id recorded with every file, symbol and finding, e.g. {@code java}. */    String languageId();

    /** True when this adapter handles the file. Only the extension is inspected; nothing is read from disk. */    boolean supports(Path file);

    /**     * Parses source text that the caller already read.
     * @param file   repository-relative path, used for the reported positions and the file record
     * @param source full source text
     */    T parse(Path file, String source);

    /** Symbols of a parsed file. Returns an empty list when the language has no symbol support (support level 1). */
    List<Symbol> extractSymbols(T file);

    /**
     * Extracts structural relationships (calls, inheritance, type references) from a parsed file (Phase 8, Level 3).
     */
    default List<com.bcosmos.core.model.Relationship> extractRelationships(T file) {
        return List.of();
    }

    /** Findings of one parsed file, filtered by the given rule set. */
    List<Finding> analyze(T file, RuleSet rules);

    /** The default rule set of this adapter, including the hash stored on every run. */    RuleSet defaultRuleSet();

    /** Version of the parser behind {@link #parse(Path, String)}, e.g. {@code com.github.javaparser:javaparser-core:3.28.2}. */    String parserVersion();

    /** Version of the analyzer behind {@link #analyze(T, RuleSet)}, e.g. {@code net.sourceforge.pmd:pmd-java:7.28.0}. */    String analyzerVersion();

    /**     * Normalizes the snippet that identifies a finding, so the fingerprint survives reformatting (spec v2 section 4.2).        * @implNote kept on the interface as a default so each adapter can override without coupling to {@code FindingNormalizer}.     */
    default String normalizeSnippet(String snippet) {
        return snippet == null ? null : snippet.strip();
    }
}
