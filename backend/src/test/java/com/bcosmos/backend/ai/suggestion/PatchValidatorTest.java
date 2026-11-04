package com.bcosmos.backend.ai.suggestion;

import static com.bcosmos.backend.ai.suggestion.UnifiedDiffTest.PATH;
import static com.bcosmos.backend.ai.suggestion.UnifiedDiffTest.SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.ai.rag.EvidenceBundle;
import com.bcosmos.backend.jobs.LanguageAdapterRegistry;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.model.Finding;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The verdict is the phase's core promise, so it is checked against the real Java adapter: the finding really is
 * gone from the re-analysed patch, a patch that smuggles in a new finding says so, and a patch that does not apply
 * to the analysed revision is rejected rather than reported as a fix (spec v2 sections 1.2 and 7.3).
 */
class PatchValidatorTest {

    private static final int LINE_COUNT = SOURCE.split("\n", -1).length;

    private static final Map<String, Integer> RUN_FILES = Map.of(PATH, LINE_COUNT);

    private final PatchValidator validator = new PatchValidator(
            new LanguageAdapterRegistry(List.of(new JavaAdapter())));

    private final Finding target = emptyCatchFinding();

    @Test
    void acceptsAPatchThatRemovesTheFindingWithoutAddingOne() {
        ValidationVerdict verdict = validate(replaceLine(10,
                "            throw new IllegalStateException(\"unreadable\", ex);", "        }"),
                List.of(citation(PATH, 10, 11)));

        assertThat(verdict.schemaOk()).isTrue();
        assertThat(verdict.evidenceOk()).isTrue();
        assertThat(verdict.diffApplies()).isTrue();
        assertThat(verdict.parses()).isTrue();
        assertThat(verdict.targetFindingGone()).isTrue();
        assertThat(verdict.newFindings()).isEmpty();
        assertThat(verdict.allChecksPassed()).isTrue();
        assertThat(verdict.notes()).contains(ValidationVerdict.COMPILE_NOT_VERIFIED, ValidationVerdict.TESTS_NOT_RUN);
    }

    @Test
    void listsANewFindingThePatchWouldIntroduce() {
        // Two changes that are five lines apart: with no context the hunks stay separate, which is what a producer
        // would have to do as well. The first adds a private field nobody reads, the second fixes the finding.
        // UnusedPrivateField is used deliberately: it is one of the baseline rules that really fire through the
        // adapter (see docs/rule-catalog.md), so the assertion tests the verdict and not a PMD quirk.
        LinkedHashMap<Integer, List<String>> replacements = new LinkedHashMap<>();
        replacements.put(5, List.of("class Reader {", "    private int unused;"));
        replacements.put(10, List.of("            throw new IllegalStateException(\"unreadable\", ex);", "        }"));

        ValidationVerdict verdict = validate(TestDiffs.diff(PATH, SOURCE, 0, replacements),
                List.of(citation(PATH, 10, 11)));

        assertThat(verdict.diffApplies()).isTrue();
        assertThat(verdict.parses()).isTrue();
        assertThat(verdict.targetFindingGone()).isTrue();
        assertThat(verdict.newFindings()).extracting("ruleId").containsExactly("UnusedPrivateField");
        assertThat(verdict.allChecksPassed()).isFalse();
        assertThat(verdict.notes()).isNotEmpty();
    }

    @Test
    void refusesAPatchThatDoesNotApplyToTheAnalysedRevision() {
        String foreign = TestDiffs.replaceLine(PATH, SOURCE, 10,
                        "            throw new IllegalStateException(\"unreadable\", ex);", "        }")
                .replace("        } catch (IOException ex) {", "        } catch (Throwable ex) {");

        ValidationVerdict verdict = validate(foreign, List.of(citation(PATH, 10, 11)));

        assertThat(verdict.diffApplies()).isFalse();
        assertThat(verdict.parses()).isFalse();
        assertThat(verdict.targetFindingGone()).isFalse();
        assertThat(verdict.notes()).anySatisfy(note -> assertThat(note).contains("does not apply"));
    }

    @Test
    void refusesADiffThatIsNotADiff() {
        ValidationVerdict verdict = validate("I would log the exception instead.", List.of(citation(PATH, 10, 11)));

        assertThat(verdict.diffApplies()).isFalse();
        assertThat(verdict.notes()).anySatisfy(note -> assertThat(note).contains("the diff was rejected"));
    }

    @Test
    void reportsAPatchThatKeepsTheFinding() {
        ValidationVerdict verdict = validate(TestDiffs.replaceLine(PATH, SOURCE, 11, "        return \"\";"),
                List.of(citation(PATH, 10, 11)));

        assertThat(verdict.diffApplies()).isTrue();
        assertThat(verdict.parses()).isTrue();
        assertThat(verdict.allChecksPassed()).isFalse();
        assertThat(verdict.notes()).anySatisfy(note -> assertThat(note).contains("still reports this finding"));
    }

    @Test
    void refusesCitationsTheRunCannotConfirm() {
        ValidationVerdict elsewhere = validate(TestDiffs.replaceLine(PATH, SOURCE, 10, "        }"),
                List.of(citation("src/main/java/com/example/Other.java", 3, 4)));

        assertThat(elsewhere.evidenceOk()).isFalse();
        assertThat(elsewhere.notes()).anySatisfy(note -> assertThat(note).contains("not a file of this run"));

        ValidationVerdict outside = validate(TestDiffs.replaceLine(PATH, SOURCE, 10, "        }"),
                List.of(citation(PATH, 1, LINE_COUNT + 50)));

        assertThat(outside.evidenceOk()).isFalse();
        assertThat(outside.notes()).anySatisfy(note -> assertThat(note).contains("outside the stored file"));
    }

    @Test
    void refusesAnAbsolutePathAndAnAnswerWithNoCitationAtAll() {
        ValidationVerdict absolute = validate(TestDiffs.replaceLine(PATH, SOURCE, 10, "        }"),
                List.of(citation("/home/someone/Reader.java", 1, 2)));

        assertThat(absolute.evidenceOk()).isFalse();
        assertThat(absolute.notes()).anySatisfy(note -> assertThat(note).contains("absolute path"));

        ValidationVerdict uncited = validate(TestDiffs.replaceLine(PATH, SOURCE, 10, "        }"), List.of());

        assertThat(uncited.evidenceOk()).isFalse();
        assertThat(uncited.notes()).anySatisfy(note -> assertThat(note).contains("cites no evidence"));
    }

    private ValidationVerdict validate(String diff, List<EvidenceRef> citations) {
        FixSuggestion suggestion = new FixSuggestion("log the failure", "the catch block is empty", citations, diff,
                List.of(), List.of(), List.of());
        return validator.validate(target.fingerprint(), PATH, SOURCE, suggestion, true, evidence(), RUN_FILES);
    }

    private static EvidenceBundle evidence() {
        return new EvidenceBundle(
                List.of(new EvidenceBundle.EvidenceItem(EvidenceBundle.EvidenceKind.ENCLOSING, 1L, PATH, 6, 11,
                        "com.example.Reader.read", SOURCE, null, 30)),
                "An exception is caught and the catch block does nothing.",
                List.of("direct callers and callees are not included"),
                30,
                "GEMINI/gemini-embedding-2",
                1,
                false);
    }

    private static String replaceLine(int line, String... replacements) {
        return TestDiffs.replaceLine(PATH, SOURCE, line, replacements);
    }

    private static EvidenceRef citation(String file, int startLine, int endLine) {
        return new EvidenceRef(file, startLine, endLine, "the catch body only holds a comment");
    }

    /** The finding the fixture really produces, used as the target of every verdict here. */
    private static Finding emptyCatchFinding() {
        JavaAdapter adapter = new JavaAdapter();
        var parsed = adapter.parse(Path.of(PATH), SOURCE);
        return adapter.analyze(parsed, adapter.defaultRuleSet()).stream()
                .filter(finding -> finding.ruleId().equals("EmptyCatchBlock"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the fixture does not produce an EmptyCatchBlock finding; the verdict tests are meaningless"));
    }
}
