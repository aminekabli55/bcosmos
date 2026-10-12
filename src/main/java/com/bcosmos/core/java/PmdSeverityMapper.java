package com.bcosmos.core.java;

import com.bcosmos.core.model.Severity;
import net.sourceforge.pmd.lang.rule.RulePriority;

/**
 * Maps PMD rule priority to the normalized scale (spec v2 section 4.3). The mapping is deliberately
 * conservative — PMD's {@code HIGH} is not promoted to {@code CRITICAL} — and is documented in
 * {@code docs/rule-catalog.md}. The original priority is still stored on every finding.
 *
 * <pre>
 * PMD HIGH (1)        -&gt; HIGH
 * PMD MEDIUM_HIGH (2) -&gt; HIGH
 * PMD MEDIUM (3)      -&gt; MEDIUM
 * PMD MEDIUM_LOW (4)  -&gt; LOW
 * PMD LOW (5)         -&gt; INFO
 * </pre>
 */
final class PmdSeverityMapper {

    private PmdSeverityMapper() {
    }

    static Severity toSeverity(RulePriority priority) {
        if (priority == null) {
            return Severity.MEDIUM;
        }
        return switch (priority) {
            case HIGH, MEDIUM_HIGH -> Severity.HIGH;
            case MEDIUM -> Severity.MEDIUM;
            case MEDIUM_LOW -> Severity.LOW;
            case LOW -> Severity.INFO;
        };
    }
}
