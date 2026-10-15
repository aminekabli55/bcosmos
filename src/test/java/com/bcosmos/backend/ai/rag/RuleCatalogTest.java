package com.bcosmos.backend.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Keeps the descriptions retrieval sends in sync with the rules BCosmos actually runs.
 *
 * <p>The rule set itself lives in analysis-core's {@code pmd/bcosmos-baseline.properties} and is described to humans
 * in {@code docs/rule-catalog.md}. Three lists that drift apart is how a prompt ends up explaining a rule the
 * analysis no longer applies, so the drift is a test failure instead of a surprise in a suggestion.</p>
 */
class RuleCatalogTest {

    private final RuleCatalog catalog = new RuleCatalog(new DefaultResourceLoader());

    @Test
    void describesEveryRuleOfTheBaseline() {
        List<String> baselineRules = baselineRuleNames();

        assertThat(baselineRules).isNotEmpty();
        assertThat(baselineRules).allSatisfy(rule -> assertThat(catalog.knows(rule))
                .withFailMessage("rule %s is in the baseline but %s has no description for it;"
                        + " add one so retrieval can explain the rule", rule, RuleCatalog.LOCATION)
                .isTrue());
    }

    @Test
    void describesNothingTheBaselineDoesNotApply() {
        assertThat(catalog.knownRuleIds()).containsExactlyInAnyOrderElementsOf(baselineRuleNames());
    }

    @Test
    void everyDescriptionIsAdocumentedSentenceAboutWhatsAndHowToFixIt() {
        assertThat(catalog.knownRuleIds()).allSatisfy(rule -> assertThat(catalog.describe(rule))
                .hasSizeGreaterThan(60));
        assertThat(catalog.describe("NoSuchRuleAtAll"))
                .isEqualTo("(no description is recorded for this rule; work from the tool's message and the code)");
    }

    @Test
    void namesEveryRuleInTheHumanReadableCatalog() throws IOException {
        Path doc = Path.of("..", "docs", "rule-catalog.md");
        assertThat(doc).exists();
        String text = Files.readString(doc, StandardCharsets.UTF_8);

        assertThat(catalog.knownRuleIds()).allSatisfy(
                rule -> assertThat(text).withFailMessage("docs/rule-catalog.md does not mention %s", rule)
                        .contains("`" + rule + "`"));
    }

    /** Rule names of the baseline definitions (PMD + ESLint), i.e. the part after the last '/' of every reference. */
    private static List<String> baselineRuleNames() {
        java.util.List<String> rules = new java.util.ArrayList<>();
        rules.addAll(loadBaselineRules("/pmd/bcosmos-baseline.properties"));
        rules.addAll(loadBaselineRules("/eslint/bcosmos-baseline.properties"));
        return rules.stream().sorted().toList();
    }

    private static List<String> loadBaselineRules(String resourcePath) {
        Properties definition = new Properties();
        try (InputStream in = RuleCatalogTest.class.getResourceAsStream(resourcePath);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            definition.load(reader);
        } catch (IOException | NullPointerException failure) {
            throw new IllegalStateException("baseline rule set " + resourcePath + " is not on the test classpath", failure);
        }
        return definition.stringPropertyNames().stream()
                .filter(name -> name.startsWith("rule."))
                .map(definition::getProperty)
                .map(reference -> reference.substring(reference.lastIndexOf('/') + 1).trim())
                .toList();
    }
}
