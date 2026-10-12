package com.bcosmos.core.java;

import com.bcosmos.core.AnalysisException;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import com.bcosmos.core.quality.ArtifactVersions;
import com.bcosmos.core.quality.Fingerprint;
import com.bcosmos.core.model.Span;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

/**
 * The only {@link LanguageAdapter} implementation of phase 1: Java, at support level 3 for symbols and level 1
 * for findings (spec v2 section 3.1).
 *
 * <ul>
 *   <li>Parsing and symbols: JavaParser ({@code javaparser-core}) with the Java 21 language level.</li>
 *   <li>Findings: PMD ({@code pmd-java}) with the baseline rule set {@code pmd/bcosmos-baseline.xml}.</li>
 *   <li>Identity: {@link Fingerprint}, rule + enclosing symbol + normalized snippet (spec 4.2).</li>
 * </ul>
 *
 * <p>No filesystem access and no execution of analyzed code: the caller reads the source and passes it in.</p>
 *
 * <p>Known cost, deliberate for this phase: PMD's rule set is loaded for every analyzed file, because a loaded
 * rule set must not be shared across concurrently running analyses. Phase 4 (worker) is the right place to add a
 * batch API that loads it once per run.</p>
 */
public final class JavaAdapter implements LanguageAdapter<JavaParsedFile> {

    public static final String LANGUAGE_ID = "java";
    public static final String TOOL_PMD = "pmd";
    public static final String BASELINE_RULE_SET_ID = "bcosmos-baseline";

    /** Classpath id of the baseline rule set definition (no leading slash: PMD resolves it as a classpath id). */
    private static final String RULE_SET_CLASSPATH_ID = "pmd/bcosmos-baseline.properties";

    /** Resource path used to hash and read the same definition directly. */
    private static final String RULE_SET_RESOURCE = "/" + RULE_SET_CLASSPATH_ID;

    /** Prefix of the numbered rule reference entries in the definition file. */
    private static final String RULE_ENTRY_PREFIX = "rule.";
    private static final String JAVA_EXTENSION = ".java";
    private static final String JAVAPARSER_GROUP_ID = "com.github.javaparser";
    private static final String JAVAPARSER_ARTIFACT_ID = "javaparser-core";
    private static final String PMD_GROUP_ID = "net.sourceforge.pmd";
    private static final String PMD_ARTIFACT_ID = "pmd-java";
    private static final int MAX_SNIPPET_LINES = 6;
    private static final int MAX_SNIPPET_CHARS = 400;

    private final ParserConfiguration parserConfiguration;
    private final String parserVersion;
    private final String analyzerVersion;
    private final RuleSet defaultRuleSet;

    public JavaAdapter() {
        this.parserConfiguration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parserVersion = ArtifactVersions.coordinates(JAVAPARSER_GROUP_ID, JAVAPARSER_ARTIFACT_ID);
        this.analyzerVersion = ArtifactVersions.coordinates(PMD_GROUP_ID, PMD_ARTIFACT_ID);
        this.defaultRuleSet = RuleSet.all(BASELINE_RULE_SET_ID, ruleSetHash());
    }

    @Override
    public String languageId() {
        return LANGUAGE_ID;
    }

    @Override
    public boolean supports(Path file) {
        return file != null && file.getFileName() != null
                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(JAVA_EXTENSION);
    }

    @Override
    public JavaParsedFile parse(Path file, String source) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
        String relativePath = toRelativePath(file);

        ParseResult<CompilationUnit> result = new JavaParser(parserConfiguration).parse(source);
        CompilationUnit ast = result.getResult().orElse(null);
        boolean parsed = result.isSuccessful() && ast != null;
        List<ParseDiagnostic> diagnostics = result.getProblems().stream().map(JavaAdapter::toDiagnostic).toList();
        List<Symbol> symbols = parsed ? JavaSymbolExtractor.extract(ast) : List.of();
        return new JavaParsedFile(relativePath, source, parsed ? ast : null,
                parsed ? ParseStatus.OK : ParseStatus.FAILED, diagnostics, symbols);
    }

    @Override
    public List<Symbol> extractSymbols(JavaParsedFile file) {
        Objects.requireNonNull(file, "file");
        return file.symbols();
    }

    @Override
    public List<Finding> analyze(JavaParsedFile file, RuleSet rules) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(rules, "rules");
        if (!file.parsed()) {
            // A file that did not parse has no findings; the run reports it as a parse failure instead.
            return List.of();
        }

        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setIgnoreIncrementalAnalysis(true);
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            analysis.addRuleSet(loadPmdRuleSet(configuration));
            analysis.files().addSourceFile(FileId.fromPathLikeString(file.filePath()), file.source());
            Report report = analysis.performAnalysisAndCollectReport();
            if (!report.getProcessingErrors().isEmpty()) {
                throw new AnalysisException("pmd could not analyze " + file.filePath() + ": "
                        + report.getProcessingErrors().getFirst().getMsg());
            }
            List<Finding> findings = new ArrayList<>();
            for (RuleViolation violation : report.getViolations()) {
                String ruleId = violation.getRule().getName();
                if (rules.includes(ruleId)) {
                    findings.add(toFinding(file, violation, ruleId));
                }
            }
            return List.copyOf(findings);
        }
    }

    @Override
    public RuleSet defaultRuleSet() {
        return defaultRuleSet;
    }

    @Override
    public String normalizeSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        return snippet.replaceAll("\\s+", " ").trim();
    }

    /** Version of the parser that produced the positions, e.g. {@code com.github.javaparser:javaparser-core:3.28.2}. */
    @Override
    public String parserVersion() {
        return parserVersion;
    }

    /** Version of the analyzer that produced the findings, e.g. {@code net.sourceforge.pmd:pmd-java:7.28.0}. */
    @Override
    public String analyzerVersion() {
        return analyzerVersion;
    }

    /** Id of the baseline rule set shipped in this jar. */
    public String ruleSetId() {
        return BASELINE_RULE_SET_ID;
    }

    /**
     * Builds the PMD rule set from the references in {@code pmd/bcosmos-baseline.properties}.
     *
     * <p>PMD's own category files are referenced by their published paths instead of copying rule definitions:
     * a reference that no longer resolves fails here, loudly, rather than producing a silently empty rule set.</p>
     */
    private net.sourceforge.pmd.lang.rule.RuleSet loadPmdRuleSet(PMDConfiguration configuration) {
        RuleSetLoader loader = RuleSetLoader.fromPmdConfig(configuration)
                .loadResourcesWith(JavaAdapter.class.getClassLoader());
        List<net.sourceforge.pmd.lang.rule.Rule> rules = new ArrayList<>();
        for (String reference : baselineRuleReferences()) {
            try {
                rules.addAll(loader.loadFromResource(reference).getRules());
            } catch (RuntimeException e) {
                throw new IllegalStateException("unknown rule reference in " + RULE_SET_CLASSPATH_ID + ": "
                        + reference + " (" + e.getMessage() + ")", e);
            }
        }
        if (rules.isEmpty()) {
            throw new IllegalStateException("baseline rule set " + RULE_SET_CLASSPATH_ID + " defines no rules");
        }
        return net.sourceforge.pmd.lang.rule.RuleSet.create(BASELINE_RULE_SET_ID, baselineDescription(),
                RULE_SET_CLASSPATH_ID, List.of(), List.of(), rules);
    }

    private Finding toFinding(JavaParsedFile file, RuleViolation violation, String ruleId) {
        int startLine = Math.max(violation.getBeginLine(), 1);
        int endLine = Math.max(violation.getEndLine(), startLine);
        Span span = new Span(startLine, violation.getBeginColumn(), endLine, violation.getEndColumn());
        String symbol = enclosingSymbol(file.symbols(), span);
        String snippet = snippetOf(file.source(), span);
        String normalizedSnippet = normalizeSnippet(snippet);
        String description = violation.getDescription() == null ? ruleId : violation.getDescription();
        return new Finding(
                TOOL_PMD,
                analyzerVersion,
                ruleId,
                violation.getRule().getPriority().name(),
                PmdSeverityMapper.toSeverity(violation.getRule().getPriority()),
                description,
                file.filePath(),
                span,
                symbol,
                snippet,
                normalizedSnippet,
                Fingerprint.of(ruleId, symbol, normalizedSnippet));
    }

    /**
     * The tightest symbol that encloses the finding, preferring members over their declaring type: that is the
     * "enclosing symbol" the fingerprint relies on (spec 4.2). {@code null} when the finding sits outside any symbol.
     */
    private static String enclosingSymbol(List<Symbol> symbols, Span span) {
        return symbols.stream()
                .filter(symbol -> symbol.encloses(span.startLine()))
                .min(Comparator.comparingInt(Symbol::lineCount)
                        .thenComparing(symbol -> symbol.kind() == SymbolKind.METHOD
                                || symbol.kind() == SymbolKind.CONSTRUCTOR
                                || symbol.kind() == SymbolKind.FIELD ? 0 : 1))
                .map(Symbol::qualifiedName)
                .orElse(null);
    }

    private static String snippetOf(String source, Span span) {
        String[] lines = source.split("\n", -1);
        int lastLine = Math.min(span.startLine() + MAX_SNIPPET_LINES - 1, Math.min(span.endLine(), lines.length));
        List<String> selected = new ArrayList<>();
        for (int line = span.startLine(); line <= lastLine; line++) {
            if (line >= 1 && line <= lines.length) {
                selected.add(lines[line - 1].strip());
            }
        }
        String snippet = String.join("\n", selected);
        return snippet.length() > MAX_SNIPPET_CHARS ? snippet.substring(0, MAX_SNIPPET_CHARS) : snippet;
    }

    private static ParseDiagnostic toDiagnostic(Problem problem) {
        return problem.getLocation()
                .map(TokenRange::getBegin)
                .flatMap(token -> token.getRange())
                .map(range -> new ParseDiagnostic(problem.getMessage(), range.begin.line, range.begin.column))
                .orElseGet(() -> ParseDiagnostic.of(problem.getMessage()));
    }

    private static String toRelativePath(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("file path must be repository-relative, was absolute");
        }
        return path;
    }

    private static String ruleSetHash() {
        try (InputStream in = JavaAdapter.class.getResourceAsStream(RULE_SET_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is missing");
            }
            return Fingerprint.sha256Hex(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is not readable", e);
        }
    }

    /** Rule references of the baseline definition file, in declared order (used by the rule-catalog test). */
    static List<String> baselineRuleReferences() {
        Properties definition = baselineDefinition();
        return definition.stringPropertyNames().stream()
                .filter(name -> name.startsWith(RULE_ENTRY_PREFIX))
                .sorted(Comparator.comparingInt(JavaAdapter::ruleEntryOrder))
                .map(definition::getProperty)
                .map(String::trim)
                .filter(reference -> !reference.isEmpty())
                .toList();
    }

    private static String baselineDescription() {
        return baselineDefinition().getProperty("description", BASELINE_RULE_SET_ID);
    }

    private static Properties baselineDefinition() {
        try (InputStream in = JavaAdapter.class.getResourceAsStream(RULE_SET_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is missing");
            }
            Properties definition = new Properties();
            definition.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return definition;
        } catch (IOException e) {
            throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is not readable", e);
        }
    }

    private static int ruleEntryOrder(String propertyName) {
        try {
            return Integer.parseInt(propertyName.substring(RULE_ENTRY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
