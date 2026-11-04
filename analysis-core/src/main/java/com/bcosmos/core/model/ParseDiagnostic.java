package com.bcosmos.core.model;

import java.util.Objects;

/**
 * One parser problem, with the position when the parser reports one.
 *
 * @param message parser message (never contains source code we log ourselves; it comes straight from the parser)
 * @param line    1-based line, or 0 when unknown
 * @param column  1-based column, or 0 when unknown
 */
public record ParseDiagnostic(String message, int line, int column) {

    public ParseDiagnostic {
        Objects.requireNonNull(message, "message");
    }

    public static ParseDiagnostic of(String message) {
        return new ParseDiagnostic(message, 0, 0);
    }

    public boolean hasPosition() {
        return line > 0;
    }
}
