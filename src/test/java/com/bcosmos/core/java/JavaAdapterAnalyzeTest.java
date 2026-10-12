package com.bcosmos.core.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.Fixtures;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.RuleSet;
import com.bcosmos.core.quality.Fingerprint;
import java.util.List;
import java.util.Set;
import net.sourceforge.pmd.lang.rule.RulePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaAdapterAnalyzeTest {

    private final JavaAdapter adapter = new JavaAdapter();
    private final RuleSet rules = new JavaAdapter().defaultRuleSet();

    @Test
    @DisplayName("the fixture with an empty catch produces exactly one EmptyCatchBlock finding with evidence")
    void reportsEmptyCatchBlock() {
        String source = Fixtures.source("EmptyCatchSample.java");
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("EmptyCatchSample.java"), source);

        List<Finding> findings = adapter.analyze(file, rules);

        assertThat(findings).hasSize(1);
        Finding finding = findings.getFirst();
        assertThat(finding.ruleId()).isEqualTo("EmptyCatchBlock");
        assertThat(finding.tool()).isEqualTo("pmd");
        assertThat(finding.toolVersion()).startsWith("net.sourceforge.pmd:pmd-java:");
        assertThat(finding.filePath()).isEqualTo("fixtures/EmptyCatchSample.java");
        assertThat(finding.message()).isNotBlank();
        assertThat(finding.span().startLine())
                .isEqualTo(Fixtures.lineOf(source, "} catch (IllegalStateException e) {"));
        assertThat(finding.symbolQualifiedName()).isEqualTo("com.acme.quality.EmptyCatchSample.loadQuietly");
        assertThat(finding.snippet()).contains("catch");
        assertThat(finding.normalizedSnippet()).doesNotContain("\n").isEqualTo(finding.normalizedSnippet().trim());
        assertThat(finding.fingerprint()).hasSize(64);
        assertThat(finding.severity()).isEqualTo(PmdSeverityMapper.toSeverity(RulePriority.valueOf(finding.originalSeverity())));
    }

    @Test
    @DisplayName("the fixture with an unused private field produces exactly one UnusedPrivateField finding")
    void reportsUnusedPrivateField() {
        String source = Fixtures.source("UnusedFieldSample.java");
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("UnusedFieldSample.java"), source);

        List<Finding> findings = adapter.analyze(file, rules);

        assertThat(findings).hasSize(1);
        Finding finding = findings.getFirst();
        assertThat(finding.ruleId()).isEqualTo("UnusedPrivateField");
        assertThat(finding.span().startLine()).isEqualTo(Fixtures.lineOf(source, "private String unusedNote;"));
        assertThat(finding.symbolQualifiedName())
                .isEqualTo("com.acme.quality.UnusedFieldSample.unusedNote");
        assertThat(finding.severity()).isEqualTo(PmdSeverityMapper.toSeverity(RulePriority.valueOf(finding.originalSeverity())));
    }

    @Test
    @DisplayName("the fingerprint is derived from rule, enclosing symbol and normalized snippet (spec 4.2)")
    void fingerprintMatchesTheDocumentedRecipe() {
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("EmptyCatchSample.java"),
                Fixtures.source("EmptyCatchSample.java"));

        Finding finding = adapter.analyze(file, rules).getFirst();

        assertThat(finding.fingerprint()).isEqualTo(Fingerprint.of(
                finding.ruleId(), finding.symbolQualifiedName(), adapter.normalizeSnippet(finding.snippet())));
    }

    @Test
    @DisplayName("findings only contain rules the rule set enables")
    void ruleSetFiltersFindings() {
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("EmptyCatchSample.java"),
                Fixtures.source("EmptyCatchSample.java"));
        String rulesetId = adapter.defaultRuleSet().id();
        String hash = adapter.defaultRuleSet().hash();

        assertThat(adapter.analyze(file, new RuleSet(rulesetId, Set.of("EmptyCatchBlock"), hash)))
                .singleElement()
                .satisfies(finding -> assertThat(finding.ruleId()).isEqualTo("EmptyCatchBlock"));
        assertThat(adapter.analyze(file, new RuleSet(rulesetId, Set.of("UnusedPrivateField"), hash))).isEmpty();
        assertThat(adapter.analyze(file, new RuleSet(rulesetId, Set.of("NoSuchRule"), hash))).isEmpty();
    }

    @Test
    @DisplayName("analysis of the same file twice returns exactly the same findings")
    void analysisIsDeterministic() {
        JavaParsedFile file = adapter.parse(Fixtures.relativePath("UnusedFieldSample.java"),
                Fixtures.source("UnusedFieldSample.java"));

        assertThat(adapter.analyze(file, rules)).isEqualTo(adapter.analyze(file, rules));
    }

    @Test
    @DisplayName("the baseline definition lists exactly the rules the rule catalog documents")
    void baselineDefinitionMatchesTheRuleCatalog() {
        assertThat(JavaAdapter.baselineRuleReferences()).containsExactly(
                "category/java/errorprone.xml/EmptyCatchBlock",
                "category/java/bestpractices.xml/UnusedPrivateField",
                "category/java/bestpractices.xml/UnusedLocalVariable",
                "category/java/codestyle.xml/UnusedImports");
    }

    @Test
    @DisplayName("the baseline rule set has a stable identity and hash")
    void baselineRuleSetIsIdentified() {
        RuleSet baseline = adapter.defaultRuleSet();

        assertThat(baseline.id()).isEqualTo("bcosmos-baseline");
        assertThat(baseline.hash()).hasSize(64);
        assertThat(baseline.enabledRuleIds()).isEmpty();
        assertThat(baseline.includes("EmptyCatchBlock")).isTrue();
        assertThat(adapter.ruleSetId()).isEqualTo(baseline.id());
        assertThat(new JavaAdapter().defaultRuleSet().hash()).isEqualTo(baseline.hash());
    }
}
