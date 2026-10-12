package com.bcosmos.core.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.model.Severity;
import net.sourceforge.pmd.lang.rule.RulePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PmdSeverityMapperTest {

    @Test
    @DisplayName("PMD priorities map to the normalized scale exactly as documented in the rule catalog")
    void mapsEveryPriority() {
        assertThat(PmdSeverityMapper.toSeverity(RulePriority.HIGH)).isEqualTo(Severity.HIGH);
        assertThat(PmdSeverityMapper.toSeverity(RulePriority.MEDIUM_HIGH)).isEqualTo(Severity.HIGH);
        assertThat(PmdSeverityMapper.toSeverity(RulePriority.MEDIUM)).isEqualTo(Severity.MEDIUM);
        assertThat(PmdSeverityMapper.toSeverity(RulePriority.MEDIUM_LOW)).isEqualTo(Severity.LOW);
        assertThat(PmdSeverityMapper.toSeverity(RulePriority.LOW)).isEqualTo(Severity.INFO);
    }

    @Test
    @DisplayName("a missing priority falls back to MEDIUM instead of failing the run")
    void fallsBackToMedium() {
        assertThat(PmdSeverityMapper.toSeverity(null)).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("the normalized scale orders severities from INFO to CRITICAL")
    void severityOrderIsMeaningful() {
        assertThat(Severity.HIGH.atLeast(Severity.LOW)).isTrue();
        assertThat(Severity.INFO.atLeast(Severity.LOW)).isFalse();
        assertThat(Severity.CRITICAL.atLeast(Severity.CRITICAL)).isTrue();
    }
}
