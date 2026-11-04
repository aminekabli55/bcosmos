package com.bcosmos.core.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.Findings;
import com.bcosmos.core.model.Finding;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingNormalizerTest {

    private final FindingNormalizer normalizer = new FindingNormalizer();

    @Test
    @DisplayName("a finding without a fingerprint gets one derived from the documented recipe")
    void assignsMissingFingerprints() {
        Finding raw = Findings.withoutFingerprint("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 5);

        List<Finding> normalized = normalizer.normalize(List.of(raw));

        assertThat(normalized).singleElement().satisfies(finding -> {
            assertThat(finding.fingerprint()).hasSize(64);
            assertThat(finding.fingerprint()).isEqualTo(
                    Fingerprint.of(finding.ruleId(), finding.symbolQualifiedName(), finding.normalizedSnippet()));
        });
    }

    @Test
    @DisplayName("the same finding reported twice by a tool is stored once")
    void removesDuplicates() {
        Finding first = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 5, 7);
        Finding duplicate = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 5, 7);

        assertThat(normalizer.normalize(List.of(first, duplicate))).hasSize(1);
    }

    @Test
    @DisplayName("two occurrences of the same rule in one file stay separate findings")
    void keepsDistinctOccurrences() {
        Finding atTheTop = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 5, 7);
        Finding furtherDown = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 40, 42);

        assertThat(normalizer.normalize(List.of(atTheTop, furtherDown))).hasSize(2);
    }

    @Test
    @DisplayName("the result is ordered by file, line, column, rule and fingerprint")
    void sortsDeterministically() {
        Finding later = Findings.pmd("UnusedPrivateField", "com.acme.UserService.field", "private int value;", 30, 30);
        Finding earlier = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 10, 12);

        List<Finding> normalized = normalizer.normalize(List.of(later, earlier));

        assertThat(normalized).extracting(Finding::ruleId)
                .containsExactly("EmptyCatchBlock", "UnusedPrivateField");
        assertThat(normalizer.normalize(List.of(earlier, later))).isEqualTo(normalized);
    }
}
