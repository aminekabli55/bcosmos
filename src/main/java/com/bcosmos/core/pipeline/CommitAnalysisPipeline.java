package com.bcosmos.core.pipeline;

import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.quality.Fingerprint;
import com.bcosmos.core.quality.FindingNormalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Spring-free single-file analysis pipeline (ADR 0025).
 *
 * <p>This is the code the backend's {@code AnalysisRunner} and the {@code analysis-worker}'s consumers
 * share. It is deliberately free of Spring: both callers instantiate it directly and supply their own
 * {@link FileSource} implementation.
 *
 * <p>Flow for one file:
 * <ol>
 *   <li>Read the source via {@link FileSource}.
 *   <li>Parse with the {@link LanguageAdapter}.
 *   <li>Analyze with the adapter's default rule set.
 *   <li>Normalize and fingerprint findings with {@link FindingNormalizer}.
 *   <li>Return a {@link CommitAnalysisResult} — never throws for a bad file, only for infrastructure failures.
 * </ol>
 */
public final class CommitAnalysisPipeline {

    private static final int MAX_PARSE_ERROR_LENGTH = 900;
    private static final int MAX_DIAGNOSTICS = 3;

    private final List<LanguageAdapter<?>> adapters;
    private final FindingNormalizer normalizer;

    public CommitAnalysisPipeline(LanguageAdapter<?> adapter) {
        this(List.of(Objects.requireNonNull(adapter, "adapter")));
    }

    public CommitAnalysisPipeline(List<LanguageAdapter<?>> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("adapters must not be null or empty");
        }
        this.adapters = List.copyOf(adapters);
        this.normalizer = new FindingNormalizer();
    }

    /**
     * Analyses one file.
     *
     * @param relativePath repository-relative path, e.g. {@code src/main/java/Foo.java}
     * @param source       {@link FileSource} to use; called at most once
     * @return result for this file; never {@code null}; never throws for parse / analyse failures
     * @throws IOException when {@code source.read()} fails (infrastructure failure, not a file problem)
     */
    public CommitAnalysisResult analyse(String relativePath, FileSource source) throws IOException {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(source, "source");

        String text = source.read(relativePath);
        String contentHash = Fingerprint.sha256Hex(text.getBytes(StandardCharsets.UTF_8));
        return analyseText(relativePath, text, contentHash);
    }

    /**
     * Analyses a file whose content is already known (used when the caller fetched the blob itself,
     * e.g. {@code JGitBlobSource}).
     */
    public CommitAnalysisResult analyseText(String relativePath, String source, String contentHash) {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(contentHash, "contentHash");
        return runPipeline(relativePath, source, contentHash);
    }

    public String analyzerVersion() {
        return adapters.getFirst().analyzerVersion();
    }

    public String rulesetHash() {
        return adapters.getFirst().defaultRuleSet().hash();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private <T extends ParsedFile> CommitAnalysisResult runPipeline(
            String relativePath, String source, String contentHash) {
        Path path = Path.of(relativePath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("relativePath must not be absolute: " + relativePath);
        }

        Optional<LanguageAdapter<?>> match = adapters.stream()
                .filter(a -> a.supports(path))
                .findFirst();

        if (match.isEmpty()) {
            return failed(relativePath, contentHash, source, "no language adapter supports: " + relativePath, adapters.getFirst());
        }

        @SuppressWarnings("unchecked")
        LanguageAdapter<T> typed = (LanguageAdapter<T>) match.get();
        T parsed;
        try {
            parsed = typed.parse(path, source);
        } catch (RuntimeException e) {
            return failed(relativePath, contentHash, source, e.getClass().getSimpleName() + ": " + e.getMessage(), typed);
        }
        if (!parsed.parsed()) {
            return failed(relativePath, contentHash, source, describeDiagnostics(parsed.diagnostics()), typed);
        }

        List<Finding> findings;
        try {
            findings = normalizer.normalize(typed.analyze(parsed, typed.defaultRuleSet()));
        } catch (RuntimeException e) {
            return new CommitAnalysisResult(relativePath, contentHash, typed.languageId(),
                    parsed.lineCount(), ParseStatus.FAILED,
                    "analysis failed: " + e.getClass().getSimpleName(), List.of(),
                    typed.analyzerVersion(), typed.defaultRuleSet().hash());
        }

        return new CommitAnalysisResult(relativePath, contentHash, typed.languageId(),
                parsed.lineCount(), ParseStatus.OK, null, findings,
                typed.analyzerVersion(), typed.defaultRuleSet().hash());
    }

    private CommitAnalysisResult failed(String relativePath, String contentHash, String source, String reason, LanguageAdapter<?> adapter) {
        int lines = countLines(source);
        String error = reason == null ? "unknown parse error"
                : reason.length() > MAX_PARSE_ERROR_LENGTH ? reason.substring(0, MAX_PARSE_ERROR_LENGTH) : reason;
        return new CommitAnalysisResult(relativePath, contentHash, adapter.languageId(),
                lines, ParseStatus.FAILED, error, List.of(),
                adapter.analyzerVersion(), adapter.defaultRuleSet().hash());
    }

    private static String describeDiagnostics(List<ParseDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "parser rejected the file";
        }
        StringBuilder sb = new StringBuilder("parse failed: ");
        diagnostics.stream().limit(MAX_DIAGNOSTICS).forEach(d -> {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            if (d.hasPosition()) {
                sb.append("line ").append(d.line()).append(": ");
            }
            sb.append(d.message());
        });
        String text = sb.toString().replaceAll("\\s+", " ").trim();
        return text.length() > MAX_PARSE_ERROR_LENGTH ? text.substring(0, MAX_PARSE_ERROR_LENGTH) : text;
    }

    private static int countLines(String source) {
        if (source.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') lines++;
        }
        return lines;
    }
}
