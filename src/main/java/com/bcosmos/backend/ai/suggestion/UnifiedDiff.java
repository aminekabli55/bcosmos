package com.bcosmos.backend.ai.suggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A unified diff, parsed and applied in memory (spec v2 section 7.3: "apply diff in memory").
 *
 * <p>This is deliberately the narrowest useful subset of the format: one file, any number of hunks, context,
 * added and removed lines. Nothing is executed, nothing is written to disk, and a patch that does not match the
 * analysed revision is rejected rather than forced in — that rejection is one of the signals the review screen
 * shows.</p>
 *
 * <p>Strictness is the point: a hunk whose declared line counts disagree with its own body is rejected at parse
 * time, because a model that miscounts its hunk will produce a patch that "applies" to something other than the
 * file it claims to change.</p>
 */
public record UnifiedDiff(String targetPath, List<Hunk> hunks) {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file";

    public UnifiedDiff {
        hunks = List.copyOf(hunks);
    }

    /**
     * Parses a diff. The model's answer is untrusted input, so a malformed diff raises
     * {@link InvalidDiffException} instead of producing a shorter patch.
     */
    public static UnifiedDiff parse(String diff) {
        if (diff == null || diff.isBlank()) {
            throw new InvalidDiffException("the answer carried no diff");
        }
        // A trailing newline is the terminator of the last line, not an empty line of its own: model answers end
        // with one, and treating it as context would make every patch that ends with a newline fail to apply.
        String normalized = diff.endsWith("\n") ? diff.substring(0, diff.length() - 1) : diff;
        String targetPath = null;
        List<Hunk> hunks = new ArrayList<>();
        List<String> body = null;
        int oldStart = 0;
        int oldCount = 0;
        int newStart = 0;
        int newCount = 0;

        for (String line : normalized.split("\n", -1)) {
            if (line.startsWith("@@")) {
                if (body != null) {
                    hunks.add(hunk(oldStart, oldCount, newStart, newCount, body));
                }
                Matcher header = HUNK_HEADER.matcher(line.strip());
                if (!header.matches()) {
                    throw new InvalidDiffException("unreadable hunk header: " + line.strip());
                }
                oldStart = Integer.parseInt(header.group(1));
                oldCount = header.group(2) == null ? 1 : Integer.parseInt(header.group(2));
                newStart = Integer.parseInt(header.group(3));
                newCount = header.group(4) == null ? 1 : Integer.parseInt(header.group(4));
                body = new ArrayList<>();
                continue;
            }
            if (body == null) {
                if (line.startsWith("--- ")) {
                    targetPath = stripPathPrefix(line.substring(4));
                }
                continue;
            }
            if (line.startsWith(NO_NEWLINE_MARKER)) {
                continue;
            }
            if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ") || line.isEmpty()) {
                body.add(line);
                continue;
            }
            throw new InvalidDiffException("unexpected line in a hunk: " + line);
        }
        if (body != null) {
            hunks.add(hunk(oldStart, oldCount, newStart, newCount, body));
        }
        if (hunks.isEmpty()) {
            throw new InvalidDiffException("the diff has no hunk");
        }
        return new UnifiedDiff(targetPath, hunks);
    }

    /**
     * Applies the diff to a source text.
     *
     * @return the patched text, or empty when the diff does not match this source (wrong line numbers, missing
     *         context, or hunks out of order). Empty means "this patch does not apply", which is a verdict the
     *         review screen shows, not an error to raise.
     */
    public Optional<String> applyTo(String source) {
        if (source == null) {
            return Optional.empty();
        }
        String[] lines = source.split("\n", -1);
        List<String> patched = new ArrayList<>(lines.length);
        int cursor = 0;

        for (Hunk hunk : hunks) {
            int target = hunk.oldStart() - 1;
            if (target < cursor || target > lines.length) {
                return Optional.empty();
            }
            for (int index = cursor; index < target; index++) {
                patched.add(lines[index]);
            }
            cursor = target;
            for (String line : hunk.lines()) {
                String content = line.isEmpty() ? "" : line.substring(1);
                char marker = line.isEmpty() ? ' ' : line.charAt(0);
                if (marker == ' ' || marker == '-') {
                    if (cursor >= lines.length || !lines[cursor].equals(content)) {
                        return Optional.empty();
                    }
                    if (marker == ' ') {
                        patched.add(content);
                    }
                    cursor++;
                } else {
                    patched.add(content);
                }
            }
        }
        for (int index = cursor; index < lines.length; index++) {
            patched.add(lines[index]);
        }
        return Optional.of(String.join("\n", patched));
    }

    /** Files the diff claims to change, for a message that names what the model thought it was patching. */
    public Optional<String> path() {
        return Optional.ofNullable(targetPath);
    }

    private static Hunk hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> body) {
        int oldLines = 0;
        int newLines = 0;
        for (String line : body) {
            char marker = line.isEmpty() ? ' ' : line.charAt(0);
            if (marker != '+') {
                oldLines++;
            }
            if (marker != '-') {
                newLines++;
            }
        }
        if (oldLines != oldCount || newLines != newCount) {
            throw new InvalidDiffException("hunk at line " + oldStart + " declares " + oldCount + " old and "
                    + newCount + " new lines but carries " + oldLines + " and " + newLines);
        }
        return new Hunk(oldStart, oldCount, newStart, newCount, body);
    }

    /**
     * The path from a {@code ---} or {@code +++} header, without the {@code a/} or {@code b/} prefix git and most
     * models add. The value is only used to say which file the diff thought it was patching, so a defensive
     * interpretation is the right one.
     */
    private static String stripPathPrefix(String path) {
        String stripped = path.strip();
        int tab = stripped.indexOf('\t');
        if (tab >= 0) {
            stripped = stripped.substring(0, tab);
        }
        if (stripped.startsWith("a/") || stripped.startsWith("b/")) {
            return stripped.substring(2);
        }
        return stripped;
    }

    /**
     * One hunk: where it starts in the old and the new file, how many lines it covers there, and its body.
     *
     * @param lines body lines, each keeping its leading marker ({@code ' '}, {@code '-'}, {@code '+'})
     */
    public record Hunk(int oldStart, int oldCount, int newStart, int newCount, List<String> lines) {

        public Hunk {
            lines = List.copyOf(lines);
        }
    }
}
