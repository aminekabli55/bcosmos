package com.bcosmos.core.model;

import java.util.Objects;

/**
 * A half-open-free source range: both ends are inclusive line/column positions, 1-based like every tool we use.
 *
 * @param startLine   1-based first line
 * @param startColumn 1-based first column (0 when the tool reports no column)
 * @param endLine     1-based last line
 * @param endColumn   1-based last column (0 when the tool reports no column)
 */
public record Span(int startLine, int startColumn, int endLine, int endColumn) {

    public Span {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must be >= startLine");
        }
        startColumn = Math.max(startColumn, 0);
        endColumn = Math.max(endColumn, 0);
    }

    public static Span ofLine(int line) {
        return new Span(line, 0, line, 0);
    }

    /** Number of lines the span covers, at least 1. */
    public int lineCount() {
        return endLine - startLine + 1;
    }

    /** True when {@code line} falls inside this span. */
    public boolean containsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    public boolean startsBefore(Span other) {
        Objects.requireNonNull(other, "other");
        return startLine != other.startLine ? startLine < other.startLine : startColumn < other.startColumn;
    }
}
