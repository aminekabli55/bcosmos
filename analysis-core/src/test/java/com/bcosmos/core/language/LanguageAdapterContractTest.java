package com.bcosmos.core.language;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.model.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared contract test suite that all {@link LanguageAdapter} implementations must pass (Phase 10, spec v2 section 3.1 & ADR 0012).
 * Verifies parsing, error recovery, symbol extraction, analysis, fingerprints, and snippet normalization.
 *
 * @param <T> adapter's parsed file type
 */
public abstract class LanguageAdapterContractTest<T extends ParsedFile> {

    protected LanguageAdapter<T> adapter;

    protected abstract LanguageAdapter<T> createAdapter();
    protected abstract String expectedLanguageId();
    protected abstract Path validFilePath();
    protected abstract String validSourceCode();
    protected abstract Path alternativeExtensionFilePath();
    protected abstract Path unsupportedFilePath();
    protected abstract Path invalidFilePath();
    protected abstract String invalidSourceCode();
    protected abstract String sourceWithViolation();
    protected abstract String expectedRuleId();

    @BeforeEach
    void setUp() {
        adapter = createAdapter();
    }

    @Test
    @DisplayName("languageId is stable and non-blank")
    void languageIdIsStableAndNonBlank() {
        assertThat(adapter.languageId()).isEqualTo(expectedLanguageId()).isNotBlank();
    }

    @Test
    @DisplayName("supports correctly identifies file extensions")
    void supportsCorrectlyIdentifiesExtensions() {
        assertThat(adapter.supports(validFilePath())).isTrue();
        assertThat(adapter.supports(alternativeExtensionFilePath())).isTrue();
        assertThat(adapter.supports(unsupportedFilePath())).isFalse();
        assertThat(adapter.supports(null)).isFalse();
    }

    @Test
    @DisplayName("parse valid source produces ParseStatus.OK, non-null AST, and no diagnostics")
    void parseValidSourceProducesOk() {
        T parsed = adapter.parse(validFilePath(), validSourceCode());
        assertThat(parsed.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(parsed.parsed()).isTrue();
        assertThat(parsed.diagnostics()).isEmpty();
        assertThat(parsed.lineCount()).isGreaterThan(0);
        assertThat(parsed.filePath()).isEqualTo(validFilePath().toString().replace('\\', '/'));
    }

    @Test
    @DisplayName("parse invalid source produces ParseStatus.FAILED and diagnostics")
    void parseInvalidSourceProducesFailed() {
        T parsed = adapter.parse(invalidFilePath(), invalidSourceCode());
        assertThat(parsed.parseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(parsed.parsed()).isFalse();
        assertThat(parsed.diagnostics()).isNotEmpty();
    }

    @Test
    @DisplayName("extractSymbols extracts symbols with non-blank names, spans, and 64-char signature hashes")
    void extractSymbolsExtractsValidSymbols() {
        T parsed = adapter.parse(validFilePath(), validSourceCode());
        List<Symbol> symbols = adapter.extractSymbols(parsed);
        assertThat(symbols).isNotEmpty();
        for (Symbol s : symbols) {
            assertThat(s.name()).isNotBlank();
            assertThat(s.qualifiedName()).isNotBlank();
            assertThat(s.span()).isNotNull();
            assertThat(s.span().startLine()).isGreaterThanOrEqualTo(1);
            assertThat(s.signatureHash()).hasSize(64);
        }
    }

    @Test
    @DisplayName("analyze produces findings matching ruleSet with valid spans, fingerprints, and snippets")
    void analyzeProducesFindingsWithFingerprints() {
        T parsed = adapter.parse(validFilePath(), sourceWithViolation());
        List<Finding> findings = adapter.analyze(parsed, adapter.defaultRuleSet());
        assertThat(findings).extracting(Finding::ruleId).contains(expectedRuleId());

        for (Finding f : findings) {
            assertThat(f.filePath()).isEqualTo(validFilePath().toString().replace('\\', '/'));
            assertThat(f.span()).isNotNull();
            assertThat(f.span().startLine()).isGreaterThanOrEqualTo(1);
            assertThat(f.fingerprint()).hasSize(64);
            assertThat(f.normalizedSnippet()).isNotBlank();
            assertThat(f.tool()).isNotBlank();
            assertThat(f.toolVersion()).isNotBlank();
        }
    }

    @Test
    @DisplayName("defaultRuleSet provides valid ruleSetId and 64-char ruleSetHash")
    void defaultRuleSetProvidesValidMetadata() {
        RuleSet rules = adapter.defaultRuleSet();
        assertThat(rules.id()).isNotBlank();
        assertThat(rules.hash()).hasSize(64);
    }

    @Test
    @DisplayName("parserVersion and analyzerVersion are non-blank coordinates")
    void versionsAreNonBlank() {
        assertThat(adapter.parserVersion()).isNotBlank();
        assertThat(adapter.analyzerVersion()).isNotBlank();
    }

    @Test
    @DisplayName("normalizeSnippet collapses internal whitespace and trims ends")
    void normalizeSnippetCollapsesWhitespace() {
        String input = "  function   foo(\n   bar : any   )   ";
        assertThat(adapter.normalizeSnippet(input)).isEqualTo("function foo( bar : any )");
    }
}
