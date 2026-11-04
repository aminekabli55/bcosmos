package com.bcosmos.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Loads the Java fixture files used by the adapter tests. The fixtures are real {@code .java} sources under
 * {@code src/test/resources/fixtures}: they are parsed and analyzed, never compiled or executed.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** Source text of {@code src/test/resources/fixtures/<fileName>}. */
    public static String source(String fileName) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + fileName)) {
            if (in == null) {
                throw new IllegalArgumentException("fixture not found: " + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A repository-relative path for a fixture, e.g. {@code fixtures/EmptyCatchSample.java}. */
    public static Path relativePath(String fileName) {
        return Path.of("fixtures", fileName);
    }

    /** 1-based line number of the first line of {@code source} that contains {@code needle}. */
    public static int lineOf(String source, String needle) {
        String[] lines = source.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(needle)) {
                return index + 1;
            }
        }
        throw new IllegalArgumentException("no line contains: " + needle);
    }
}
