package com.bcosmos.core.model;

import java.util.List;

/**
 * Language-independent view of one parsed file. Each adapter returns its own implementation carrying the real
 * syntax tree (for Java: a JavaParser {@code CompilationUnit}).
 *
 * <p>{@link #filePath()} is repository-relative with {@code /} separators; an absolute path is never produced,
 * stored or logged (spec v2 section 10).</p>
 */
public interface ParsedFile {

    /** Adapter id, e.g. {@code java}. */
    String languageId();

    /** Repository-relative path with {@code /} separators, e.g. {@code src/main/java/com/acme/User.java}. */
    String filePath();

    /** The exact source text that was parsed. */
    String source();

    ParseStatus parseStatus();

    /** Parser problems, empty when {@link #parseStatus()} is {@link ParseStatus#OK}. */
    List<ParseDiagnostic> diagnostics();

    default boolean parsed() {
        return parseStatus() == ParseStatus.OK;
    }

    /** Number of lines in the source, at least 1 (the database stores this per file). */
    default int lineCount() {
        String text = source();
        if (text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
