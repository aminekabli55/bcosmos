package com.bcosmos.backend.ai.rag;

/**
 * Formats a vector the way pgvector's text input accepts it: {@code [1,2,3]}.
 *
 * <p>One place, so the write path and the query path cannot disagree about the notation, and so a bad vector fails
 * with a clear message here instead of a database error later.</p>
 */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    /** The literal for a non-empty vector. */
    public static String of(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("a vector must have at least one value");
        }
        StringBuilder literal = new StringBuilder(vector.length * 12 + 2);
        literal.append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(vector[index]);
        }
        return literal.append(']').toString();
    }
}
