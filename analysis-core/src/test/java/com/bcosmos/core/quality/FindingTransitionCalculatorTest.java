package com.bcosmos.core.quality;

import com.bcosmos.core.model.FindingTransition;
import com.bcosmos.core.model.FindingTransitionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FindingTransitionCalculatorTest {

    @Test
    void matchesPersistentFindingsWithIdenticalFingerprint() {
        var from = List.of(
                new FindingTransitionCalculator.FindingItem(1L, "fp-1", "EmptyCatchBlock", "Foo.java", "msg1"),
                new FindingTransitionCalculator.FindingItem(2L, "fp-2", "UnusedPrivateMethod", "Bar.java", "msg2")
        );
        var to = List.of(
                new FindingTransitionCalculator.FindingItem(10L, "fp-1", "EmptyCatchBlock", "Foo.java", "msg1")
        );

        List<FindingTransition> transitions = FindingTransitionCalculator.compare(from, to, Set.of(), Set.of("EmptyCatchBlock", "UnusedPrivateMethod"));

        assertThat(transitions).hasSize(2);

        // fp-1 is PERSISTENT
        assertThat(transitions)
                .filteredOn(t -> "fp-1".equals(t.fingerprint()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.type()).isEqualTo(FindingTransitionType.PERSISTENT);
                    assertThat(t.fromFindingId()).isEqualTo(1L);
                    assertThat(t.toFindingId()).isEqualTo(10L);
                    assertThat(t.confidence()).isEqualTo(1.0);
                });

        // fp-2 is RESOLVED (file parsed cleanly, rule is active)
        assertThat(transitions)
                .filteredOn(t -> "fp-2".equals(t.fingerprint()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.type()).isEqualTo(FindingTransitionType.RESOLVED);
                    assertThat(t.fromFindingId()).isEqualTo(2L);
                    assertThat(t.toFindingId()).isNull();
                    assertThat(t.confidence()).isEqualTo(1.0);
                });
    }

    @Test
    void detectsNewFindings() {
        var from = List.<FindingTransitionCalculator.FindingItem>of();
        var to = List.of(
                new FindingTransitionCalculator.FindingItem(100L, "fp-new", "EmptyCatchBlock", "New.java", "msg")
        );

        List<FindingTransition> transitions = FindingTransitionCalculator.compare(from, to, Set.of(), Set.of());

        assertThat(transitions).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(FindingTransitionType.NEW);
            assertThat(t.fromFindingId()).isNull();
            assertThat(t.toFindingId()).isEqualTo(100L);
            assertThat(t.confidence()).isEqualTo(1.0);
        });
    }

    @Test
    void neverMarksResolvedWhenFileFailedToParse() {
        var from = List.of(
                new FindingTransitionCalculator.FindingItem(1L, "fp-broken", "EmptyCatchBlock", "Broken.java", "msg")
        );
        var to = List.<FindingTransitionCalculator.FindingItem>of();
        Set<String> failedFiles = Set.of("Broken.java");

        List<FindingTransition> transitions = FindingTransitionCalculator.compare(from, to, failedFiles, Set.of("EmptyCatchBlock"));

        assertThat(transitions).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(FindingTransitionType.UNCERTAIN);
            assertThat(t.fromFindingId()).isEqualTo(1L);
            assertThat(t.toFindingId()).isNull();
            assertThat(t.confidence()).isEqualTo(0.5);
            assertThat(t.reason()).contains("failed to parse");
        });
    }

    @Test
    void marksUncertainWhenRuleWasDisabledInLaterRun() {
        var from = List.of(
                new FindingTransitionCalculator.FindingItem(1L, "fp-disabled", "OldRule", "Clean.java", "msg")
        );
        var to = List.<FindingTransitionCalculator.FindingItem>of();
        Set<String> activeRulesInTarget = Set.of("NewRuleOnly");

        List<FindingTransition> transitions = FindingTransitionCalculator.compare(from, to, Set.of(), activeRulesInTarget);

        assertThat(transitions).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(FindingTransitionType.UNCERTAIN);
            assertThat(t.fromFindingId()).isEqualTo(1L);
            assertThat(t.confidence()).isEqualTo(0.5);
            assertThat(t.reason()).contains("Rule disabled");
        });
    }
}
