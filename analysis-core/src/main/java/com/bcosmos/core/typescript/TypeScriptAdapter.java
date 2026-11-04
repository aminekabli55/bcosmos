package com.bcosmos.core.typescript;

import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Relationship;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Span;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import com.bcosmos.core.quality.ArtifactVersions;
import com.bcosmos.core.quality.FindingNormalizer;
import com.bcosmos.core.quality.Fingerprint;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterTypescript;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * The TypeScript implementation of {@link LanguageAdapter} (Phase 10, spec v2 section 3.1 & 12).
 * Provides support level 1 (findings, AI explanation, fix validation) and level 2 (symbols, file structure).
 */
public final class TypeScriptAdapter implements LanguageAdapter<TypeScriptParsedFile> {

    public static final String LANGUAGE_ID = "typescript";
    public static final String TOOL_ESLINT = "eslint";
    public static final String BASELINE_RULE_SET_ID = "bcosmos-eslint-baseline";

    private static final String RULE_SET_CLASSPATH_ID = "eslint/bcosmos-baseline.properties";
    private static final String RULE_SET_RESOURCE = "/" + RULE_SET_CLASSPATH_ID;
    private static final String RULE_ENTRY_PREFIX = "rule.";

    private static final String TS_EXTENSION = ".ts";
    private static final String TSX_EXTENSION = ".tsx";
    private static final String D_TS_EXTENSION = ".d.ts";

    private static final int MAX_SNIPPET_LINES = 6;
    private static final int MAX_SNIPPET_CHARS = 400;

    private final String parserVersion;
    private final String analyzerVersion;
    private final RuleSet defaultRuleSet;
    private final FindingNormalizer normalizer = new FindingNormalizer();

    public TypeScriptAdapter() {
        this.parserVersion = ArtifactVersions.coordinates("io.github.bonede", "tree-sitter-typescript");
        this.analyzerVersion = "eslint:9.12.0/typescript-eslint:8.10.0";
        this.defaultRuleSet = RuleSet.all(BASELINE_RULE_SET_ID, ruleSetHash());
    }

    @Override
    public String languageId() {
        return LANGUAGE_ID;
    }

    @Override
    public boolean supports(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(TS_EXTENSION) || name.endsWith(TSX_EXTENSION)) && !name.endsWith(D_TS_EXTENSION);
    }

    @Override
    public TypeScriptParsedFile parse(Path file, String source) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
        String relativePath = toRelativePath(file);

        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterTypescript());

        TSTree tree = parser.parseString(null, source);
        boolean hasError = tree != null && tree.getRootNode().hasError();

        List<ParseDiagnostic> diagnostics = new ArrayList<>();
        if (hasError) {
            collectErrorDiagnostics(tree.getRootNode(), diagnostics);
            if (diagnostics.isEmpty()) {
                diagnostics.add(ParseDiagnostic.of("Syntax error in TypeScript source"));
            }
        }

        boolean parsed = !hasError && tree != null;
        List<Symbol> symbols = parsed ? TypeScriptSymbolExtractor.extract(tree, source) : List.of();

        return new TypeScriptParsedFile(
                relativePath,
                source,
                parsed ? tree : null,
                parsed ? ParseStatus.OK : ParseStatus.FAILED,
                diagnostics,
                symbols);
    }

    @Override
    public List<Symbol> extractSymbols(TypeScriptParsedFile file) {
        Objects.requireNonNull(file, "file");
        return file.symbols();
    }

    @Override
    public List<Relationship> extractRelationships(TypeScriptParsedFile file) {
        Objects.requireNonNull(file, "file");
        // Support Level 1-2: full relationship extraction is reserved for Java (Level 3, spec §3.1)
        return List.of();
    }

    @Override
    public List<Finding> analyze(TypeScriptParsedFile file, RuleSet rules) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(rules, "rules");

        if (!file.parsed() || file.tree() == null) {
            return List.of();
        }

        List<Finding> rawFindings = new ArrayList<>();
        TSNode root = file.tree().getRootNode();
        String source = file.source();

        inspectNode(root, file, rules, source, rawFindings);

        return normalizer.normalize(rawFindings);
    }

    private void inspectNode(TSNode node, TypeScriptParsedFile file, RuleSet rules, String source, List<Finding> findings) {
        if (node == null) {
            return;
        }

        String type = node.getType();

        // 1. Rule: no-empty (Empty catch block)
        if ("catch_clause".equals(type) && rules.includes("no-empty")) {
            checkEmptyCatch(node, file, source, findings);
        }

        // 2. Rule: no-explicit-any (TypeScript explicit any type)
        if ("type_annotation".equals(type) && rules.includes("no-explicit-any")) {
            checkExplicitAny(node, file, source, findings);
        }

        // 3. Rule: no-console (console.log usage)
        if ("call_expression".equals(type) && rules.includes("no-console")) {
            checkConsoleCall(node, file, source, findings);
        }

        // 4. Rule: no-unused-vars (Unused lexical variable declaration)
        if (("function_declaration".equals(type) || "method_definition".equals(type)) && rules.includes("no-unused-vars")) {
            checkUnusedVarsInFunction(node, file, source, findings);
        }

        // Recurse child nodes
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            inspectNode(node.getNamedChild(i), file, rules, source, findings);
        }
    }

    private void checkEmptyCatch(TSNode catchNode, TypeScriptParsedFile file, String source, List<Finding> findings) {
        TSNode block = findChildByType(catchNode, "statement_block");
        if (block != null) {
            boolean hasStatements = false;
            for (int i = 0; i < block.getNamedChildCount(); i++) {
                TSNode child = block.getNamedChild(i);
                if (!"comment".equals(child.getType())) {
                    hasStatements = true;
                    break;
                }
            }
            if (!hasStatements) {
                String paramName = "";
                for (int i = 0; i < catchNode.getNamedChildCount(); i++) {
                    TSNode child = catchNode.getNamedChild(i);
                    if ("identifier".equals(child.getType())) {
                        paramName = textOf(child, source);
                        break;
                    }
                }
                // Skip if intentionally ignored (spec rule parity)
                if ("ignore".equalsIgnoreCase(paramName) || "ignored".equalsIgnoreCase(paramName) || "expected".equalsIgnoreCase(paramName)) {
                    return;
                }

                Span span = spanOf(catchNode);
                addFinding(file, "no-empty", "error", Severity.HIGH,
                        "Empty catch block clause should be avoided or handle the error",
                        span, findings);
            }
        }
    }

    private void checkExplicitAny(TSNode typeNode, TypeScriptParsedFile file, String source, List<Finding> findings) {
        String text = textOf(typeNode, source);
        if (text.contains("any")) {
            for (int i = 0; i < typeNode.getNamedChildCount(); i++) {
                TSNode child = typeNode.getNamedChild(i);
                if ("predefined_type".equals(child.getType()) && "any".equals(textOf(child, source))) {
                    Span span = spanOf(child);
                    addFinding(file, "no-explicit-any", "warn", Severity.MEDIUM,
                            "Unexpected any. Specify a different type instead to preserve type safety",
                            span, findings);
                    break;
                }
            }
        }
    }

    private void checkConsoleCall(TSNode callNode, TypeScriptParsedFile file, String source, List<Finding> findings) {
        TSNode expr = callNode.getNamedChild(0);
        if (expr != null && "member_expression".equals(expr.getType())) {
            TSNode obj = expr.getNamedChild(0);
            TSNode prop = expr.getNamedChild(1);
            if (obj != null && prop != null
                    && "console".equals(textOf(obj, source))
                    && Set.of("log", "info", "warn", "error", "debug").contains(textOf(prop, source))) {
                Span span = spanOf(callNode);
                addFinding(file, "no-console", "warn", Severity.LOW,
                        "Unexpected console statement; avoid leaving debug logs in production code",
                        span, findings);
            }
        }
    }

    private void checkUnusedVarsInFunction(TSNode funcNode, TypeScriptParsedFile file, String source, List<Finding> findings) {
        TSNode body = findChildByType(funcNode, "statement_block");
        if (body == null) {
            return;
        }

        List<TSNode> declarations = new ArrayList<>();
        findNodesByType(body, "variable_declarator", declarations);

        for (TSNode decl : declarations) {
            TSNode idNode = decl.getNamedChild(0);
            if (idNode != null && "identifier".equals(idNode.getType())) {
                String varName = textOf(idNode, source);
                if (varName.startsWith("_")) {
                    continue; // standard prefix for intentional unused variable
                }

                int occurrences = countIdentifierOccurrences(body, varName, source);
                if (occurrences <= 1) { // 1 occurrence is the declaration itself
                    Span span = spanOf(idNode);
                    addFinding(file, "no-unused-vars", "warn", Severity.MEDIUM,
                            "'" + varName + "' is assigned a value but never used",
                            span, findings);
                }
            }
        }
    }

    private int countIdentifierOccurrences(TSNode scope, String identifierName, String source) {
        int count = 0;
        List<TSNode> idNodes = new ArrayList<>();
        findNodesByType(scope, "identifier", idNodes);
        for (TSNode n : idNodes) {
            if (identifierName.equals(textOf(n, source))) {
                count++;
            }
        }
        return count;
    }

    private void addFinding(TypeScriptParsedFile file, String ruleId, String originalSeverity, Severity severity,
            String message, Span span, List<Finding> findings) {
        String symbol = enclosingSymbol(file.symbols(), span);
        String snippet = snippetOf(file.source(), span);
        String normalizedSnippet = normalizeSnippet(snippet);
        findings.add(new Finding(
                TOOL_ESLINT,
                analyzerVersion,
                ruleId,
                originalSeverity,
                severity,
                message,
                file.filePath(),
                span,
                symbol,
                snippet,
                normalizedSnippet,
                Fingerprint.of(ruleId, symbol, normalizedSnippet)));
    }

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

    private void collectErrorDiagnostics(TSNode node, List<ParseDiagnostic> diagnostics) {
        if (node == null) return;
        if (node.isError() || "ERROR".equals(node.getType()) || "MISSING".equals(node.getType())) {
            int line = node.getStartPoint().getRow() + 1;
            int col = node.getStartPoint().getColumn() + 1;
            diagnostics.add(new ParseDiagnostic("Syntax error: unexpected token near " + node.getType(), line, col));
            return;
        }
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            collectErrorDiagnostics(node.getNamedChild(i), diagnostics);
        }
    }

    private static TSNode findChildByType(TSNode parent, String type) {
        for (int i = 0; i < parent.getNamedChildCount(); i++) {
            TSNode child = parent.getNamedChild(i);
            if (type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private static void findNodesByType(TSNode root, String type, List<TSNode> out) {
        if (root == null) return;
        if (type.equals(root.getType())) {
            out.add(root);
        }
        for (int i = 0; i < root.getNamedChildCount(); i++) {
            findNodesByType(root.getNamedChild(i), type, out);
        }
    }

    private static Span spanOf(TSNode node) {
        return TypeScriptSymbolExtractor.spanOf(node);
    }

    private static String textOf(TSNode node, String source) {
        return TypeScriptSymbolExtractor.textOf(node, source);
    }

    private static String toRelativePath(Path file) {
        String path = file.toString().replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("file path must be repository-relative, was absolute");
        }
        return path;
    }

    private static String ruleSetHash() {
        try (InputStream in = TypeScriptAdapter.class.getResourceAsStream(RULE_SET_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is missing");
            }
            return Fingerprint.sha256Hex(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("baseline rule set " + RULE_SET_RESOURCE + " is not readable", e);
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

    @Override
    public String parserVersion() {
        return parserVersion;
    }

    @Override
    public String analyzerVersion() {
        return analyzerVersion;
    }
}
