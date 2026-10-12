package com.bcosmos.core.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcosmos.core.Fixtures;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaAdapterParseTest {

    private final JavaAdapter adapter = new JavaAdapter();

    @Test
    @DisplayName("adapter id and file support are Java-only and extension based")
    void languageIdAndSupports() {
        assertThat(adapter.languageId()).isEqualTo("java");
        assertThat(adapter.supports(Path.of("src/main/java/com/acme/User.java"))).isTrue();
        assertThat(adapter.supports(Path.of("src/main/java/com/acme/User.JAVA"))).isTrue();
        assertThat(adapter.supports(Path.of("src/main/java/com/acme/User.kt"))).isFalse();
        assertThat(adapter.supports(Path.of("src/main/java/com/acme/User.java.bak"))).isFalse();
        assertThat(adapter.supports(null)).isFalse();
    }

    @Test
    @DisplayName("a valid file parses and reports every symbol kind with qualified names")
    void parsesAndExtractsSymbols() {
        String source = Fixtures.source("SymbolsSample.java");
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("SymbolsSample.java"), source);

        assertThat(file.parseStatus()).isEqualTo(ParseStatus.OK);
        assertThat(file.parsed()).isTrue();
        assertThat(file.languageId()).isEqualTo("java");
        assertThat(file.filePath()).isEqualTo("fixtures/SymbolsSample.java");
        assertThat(file.diagnostics()).isEmpty();
        assertThat(file.ast()).isPresent();
        assertThat(file.lineCount()).isEqualTo(source.split("\n", -1).length);

        List<Symbol> symbols = adapter.extractSymbols(file);
        Map<String, Symbol> byName = symbols.stream()
                .collect(Collectors.toMap(symbol -> symbol.kind() + " " + symbol.qualifiedName(),
                        Function.identity()));

        assertThat(byName.keySet()).contains(
                "CLASS com.acme.samples.SymbolsSample",
                "CONSTRUCTOR com.acme.samples.SymbolsSample.SymbolsSample",
                "METHOD com.acme.samples.SymbolsSample.run",
                "METHOD com.acme.samples.SymbolsSample.helper",
                "FIELD com.acme.samples.SymbolsSample.NAME",
                "FIELD com.acme.samples.SymbolsSample.count",
                "CLASS com.acme.samples.SymbolsSample.Nested",
                "METHOD com.acme.samples.SymbolsSample.Nested.nested",
                "ENUM com.acme.samples.SymbolsSample.Kind",
                "RECORD com.acme.samples.SymbolsSample.Point",
                "ANNOTATION com.acme.samples.SymbolsSample.Marker",
                "INTERFACE com.acme.samples.SampleContract");

        Symbol enclosingClass = byName.get("CLASS com.acme.samples.SymbolsSample");
        assertThat(enclosingClass.parentQualifiedName()).isNull();
        assertThat(enclosingClass.span().startLine())
                .isEqualTo(Fixtures.lineOf(source, "public class SymbolsSample"));
        assertThat(enclosingClass.lineCount()).isGreaterThan(1);
        assertThat(enclosingClass.signatureHash()).hasSize(64);

        Symbol nested = byName.get("CLASS com.acme.samples.SymbolsSample.Nested");
        assertThat(nested.parentQualifiedName()).isEqualTo("com.acme.samples.SymbolsSample");

        Symbol method = byName.get("METHOD com.acme.samples.SymbolsSample.helper");
        assertThat(method.parentQualifiedName()).isEqualTo("com.acme.samples.SymbolsSample");
        assertThat(method.span().containsLine(Fixtures.lineOf(source, "private void helper"))).isTrue();
    }

    @Test
    @DisplayName("symbol extraction is deterministic: two parses of the same source are equal")
    void symbolExtractionIsDeterministic() {
        String source = Fixtures.source("SymbolsSample.java");
        List<Symbol> first = adapter.extractSymbols(adapter.parse(Fixtures.relativePath("SymbolsSample.java"), source));
        List<Symbol> second = adapter.extractSymbols(adapter.parse(Fixtures.relativePath("SymbolsSample.java"), source));

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEmpty();
    }

    @Test
    @DisplayName("a syntax error is reported as a parse failure with a position and no symbols")
    void reportsParseFailure() {
        String source = Fixtures.source("BrokenSample.java");
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("BrokenSample.java"), source);

        assertThat(file.parseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(file.parsed()).isFalse();
        assertThat(file.diagnostics()).isNotEmpty();
        assertThat(file.diagnostics().getFirst().message()).isNotBlank();
        assertThat(file.diagnostics().getFirst().hasPosition()).isTrue();
        assertThat(file.diagnostics().getFirst().line()).isPositive();
        assertThat(file.symbols()).isEmpty();
        assertThat(file.ast()).isEmpty();
        assertThat(adapter.analyze(file, adapter.defaultRuleSet())).isEmpty();
    }

    @Test
    @DisplayName("paths stay repository-relative: an absolute path is refused")
    void refusesAbsolutePaths() {
        String source = Fixtures.source("SymbolsSample.java");
        Path absolute = Path.of("C:/repos/acme/src/main/java/com/acme/quality/BrokenSample.java");

        assertThatThrownBy(() -> adapter.parse(absolute, source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repository-relative");
    }
}
