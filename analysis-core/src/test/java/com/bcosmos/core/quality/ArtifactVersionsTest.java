package com.bcosmos.core.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.java.JavaAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArtifactVersionsTest {

    @Test
    @DisplayName("the parser and analyzer versions are read from the artifacts themselves, not from a constant")
    void readsVersionsFromTheClasspathArtifacts() {
        String javaparser = ArtifactVersions.of("com.github.javaparser", "javaparser-core");
        String pmdJava = ArtifactVersions.of("net.sourceforge.pmd", "pmd-java");

        assertThat(javaparser).isNotEqualTo(ArtifactVersions.UNKNOWN).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(pmdJava).isNotEqualTo(ArtifactVersions.UNKNOWN).matches("\\d+\\.\\d+\\.\\d+.*");
        assertThat(ArtifactVersions.coordinates("com.github.javaparser", "javaparser-core"))
                .isEqualTo("com.github.javaparser:javaparser-core:" + javaparser);
    }

    @Test
    @DisplayName("an artifact that is not on the classpath reports unknown instead of throwing")
    void reportsUnknownForMissingArtifacts() {
        assertThat(ArtifactVersions.of("com.example", "not-on-the-classpath")).isEqualTo(ArtifactVersions.UNKNOWN);
    }

    @Test
    @DisplayName("the adapter reports the same tool versions it records on findings")
    void adapterReportsItsToolVersions() {
        JavaAdapter adapter = new JavaAdapter();

        assertThat(adapter.parserVersion()).startsWith("com.github.javaparser:javaparser-core:");
        assertThat(adapter.parserVersion()).doesNotContain(ArtifactVersions.UNKNOWN);
        assertThat(adapter.analyzerVersion()).startsWith("net.sourceforge.pmd:pmd-java:");
        assertThat(adapter.analyzerVersion()).doesNotContain(ArtifactVersions.UNKNOWN);
    }
}
