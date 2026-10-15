package com.bcosmos.backend.ai.suggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds unified diffs from a source text, so tests exercise the applier against diffs that are actually correct
 * instead of hand-counted hunks that only prove the author can count.
 *
 * <p>It is a test-only producer of the same format {@link UnifiedDiff} consumes, which is what makes the applier's
 * tests meaningful: a hunk header that does not match its body is rejected by the parser, and this helper cannot
 * emit one.</p>
 */
final class TestDiffs {

    private static final int CONTEXT_LINES = 3;

    private TestDiffs() {
    }

    /** A patch that replaces one line with one or more lines, with three lines of context around it. */
    static String replaceLine(String path, String source, int lineNumber, String... replacements) {
        return diff(path, source, CONTEXT_LINES, Map.of(lineNumber, List.of(replacements)));
    }

    /**
     * A patch whose hunks carry the given number of context lines.
     *
     * <p>Zero context is how a test changes two lines that are close together: hunks whose context overlaps are
     * rejected, because a real producer would have merged them into one hunk.</p>
     */
    static String diff(String path, String source, int contextLines, Map<Integer, List<String>> replacements) {
        StringBuilder diff = new StringBuilder("--- a/").append(path).append('\n')
                .append("+++ b/").append(path).append('\n');
        for (Map.Entry<Integer, List<String>> replacement : replacements.entrySet()) {
            diff.append(hunk(source, replacement.getKey(), replacement.getValue(), contextLines)).append('\n');
        }
        return diff.toString();
    }

    /**
     * A patch with one hunk per replaced line.
     *
     * <p>The replacements must be far enough apart that their context does not overlap; the applier rejects
     * overlapping hunks, so a test that wants two changes picks two distant lines.</p>
     */
    static String diff(String path, String source, Map<Integer, List<String>> replacements) {
        return diff(path, source, CONTEXT_LINES, replacements);
    }

    private static String hunk(String source, int lineNumber, List<String> replacements, int contextLines) {
        String[] lines = source.split("\n", -1);
        int first = Math.max(1, lineNumber - contextLines);
        int last = Math.min(lines.length, lineNumber + contextLines);
        List<String> body = new ArrayList<>();
        for (int line = first; line <= last; line++) {
            if (line == lineNumber) {
                body.add("-" + lines[line - 1]);
                replacements.forEach(replacement -> body.add("+" + replacement));
            } else {
                body.add(" " + lines[line - 1]);
            }
        }
        long oldLines = body.stream().filter(entry -> !entry.startsWith("+")).count();
        long newLines = body.stream().filter(entry -> !entry.startsWith("-")).count();
        return "@@ -" + first + "," + oldLines + " +" + first + "," + newLines + " @@\n"
                + String.join("\n", body);
    }
}
