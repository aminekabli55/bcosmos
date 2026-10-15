package com.bcosmos.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HotspotScoreTest {

    @Test
    void calculatesCompositeHotspotScore() {
        // 5 findings, 2 high severity, 4 dependents (inDegree), 200 lines
        // formula: findings * 10 + highSeverity * 20 + inDegree * 5 + lineCount * 0.05
        // 5*10 (50) + 2*20 (40) + 4*5 (20) + 200*0.05 (10) = 120.0
        double score = HotspotScore.calculate(5, 2, 4, 200);
        assertThat(score).isEqualTo(120.0);

        HotspotScore hs = new HotspotScore("com.acme.Service", "Service.java", 200, 5, 2, 4, 3, score);
        assertThat(hs.symbolQualifiedName()).isEqualTo("com.acme.Service");
        assertThat(hs.findingsCount()).isEqualTo(5);
        assertThat(hs.highSeverityCount()).isEqualTo(2);
        assertThat(hs.inDegree()).isEqualTo(4);
        assertThat(hs.outDegree()).isEqualTo(3);
        assertThat(hs.score()).isEqualTo(120.0);
    }
}
