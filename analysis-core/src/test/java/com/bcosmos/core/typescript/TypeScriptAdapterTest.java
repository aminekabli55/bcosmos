package com.bcosmos.core.typescript;

import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.model.Severity;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TypeScriptAdapterTest {

    private final TypeScriptAdapter adapter = new TypeScriptAdapter();
    private final RuleSet rules = adapter.defaultRuleSet();

    @Test
    @DisplayName("every baseline rule fires on a source that violates it")
    void everyBaselineRuleFires() {
        String source = """
                export class QualitySample {
                    private rawData: any; // triggers no-explicit-any

                    public run(): void {
                        let unusedVal = 99; // triggers no-unused-vars
                        console.log('running'); // triggers no-console
                        try {
                            const _used = 1;
                        } catch (err) {
                            // triggers no-empty
                        }
                    }
                }
                """;

        TypeScriptParsedFile file = adapter.parse(Path.of("src/sample.ts"), source);
        assertThat(file.parseStatus()).isEqualTo(ParseStatus.OK);

        List<Finding> findings = adapter.analyze(file, rules);
        assertThat(findings).extracting(Finding::ruleId)
                .containsExactlyInAnyOrder("no-explicit-any", "no-unused-vars", "no-console", "no-empty");

        Finding emptyCatch = findings.stream().filter(f -> f.ruleId().equals("no-empty")).findFirst().orElseThrow();
        assertThat(emptyCatch.severity()).isEqualTo(Severity.HIGH);
        assertThat(emptyCatch.tool()).isEqualTo("eslint");
        assertThat(emptyCatch.symbolQualifiedName()).isEqualTo("QualitySample.run");
        assertThat(emptyCatch.fingerprint()).hasSize(64);

        Finding explicitAny = findings.stream().filter(f -> f.ruleId().equals("no-explicit-any")).findFirst().orElseThrow();
        assertThat(explicitAny.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(explicitAny.symbolQualifiedName()).isEqualTo("QualitySample.rawData");

        Finding unusedVar = findings.stream().filter(f -> f.ruleId().equals("no-unused-vars")).findFirst().orElseThrow();
        assertThat(unusedVar.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(unusedVar.symbolQualifiedName()).isEqualTo("QualitySample.run");

        Finding consoleLog = findings.stream().filter(f -> f.ruleId().equals("no-console")).findFirst().orElseThrow();
        assertThat(consoleLog.severity()).isEqualTo(Severity.LOW);
        assertThat(consoleLog.symbolQualifiedName()).isEqualTo("QualitySample.run");
    }

    @Test
    @DisplayName("ignored catch parameter names do not trigger no-empty")
    void ignoresExpectedCatchNames() {
        String source = """
                export class SafeHandler {
                    public handle(): void {
                        try {
                            const a = 1;
                        } catch (ignored) {
                        }
                    }
                }
                """;

        TypeScriptParsedFile file = adapter.parse(Path.of("src/safe.ts"), source);
        List<Finding> findings = adapter.analyze(file, rules);
        assertThat(findings.stream().filter(f -> f.ruleId().equals("no-empty"))).isEmpty();
    }

    @Test
    @DisplayName("extracts all symbol kinds from TypeScript AST")
    void extractsSymbolsAccurately() {
        String source = """
                export interface Item {
                    id: number;
                }

                export enum Color {
                    RED, GREEN
                }

                export class ItemService {
                    private count: number;

                    constructor() {}

                    public getCount(): number {
                        return this.count;
                    }
                }

                export function standalone(): void {}
                """;

        TypeScriptParsedFile file = adapter.parse(Path.of("src/items.ts"), source);
        List<Symbol> symbols = adapter.extractSymbols(file);

        assertThat(symbols).extracting(Symbol::name)
                .contains("Item", "id", "Color", "ItemService", "count", "constructor", "getCount", "standalone");

        assertThat(symbols).extracting(Symbol::kind)
                .contains(SymbolKind.INTERFACE, SymbolKind.FIELD, SymbolKind.ENUM, SymbolKind.CLASS,
                        SymbolKind.CONSTRUCTOR, SymbolKind.METHOD);
    }
}
