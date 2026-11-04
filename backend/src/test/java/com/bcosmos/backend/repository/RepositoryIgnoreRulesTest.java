package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryIgnoreRulesTest {

    private final RepositoryIgnoreRules rules = new RepositoryIgnoreRules(List.of());

    @Test
    void ignoresDefaultDirectories() {
        assertThat(rules.isIgnoredDirectoryName("target")).isTrue();
        assertThat(rules.isIgnoredDirectoryName("node_modules")).isTrue();
        assertThat(rules.isIgnoredDirectoryName(".git")).isTrue();
    }

    @Test
    void isCaseInsensitive() {
        assertThat(rules.isIgnoredDirectoryName("TARGET")).isTrue();
        assertThat(rules.isIgnoredDirectoryName("Node_Modules")).isTrue();
    }

    @Test
    void ignoresAnyPathSegment() {
        assertThat(rules.isIgnored(Path.of("src", "main", "java", "target", "Foo.java"))).isTrue();
        assertThat(rules.isIgnored(Path.of("target", "classes", "Foo.class"))).isTrue();
        assertThat(rules.isIgnored(Path.of("src", "main", "java", "com", "example", "Foo.java"))).isFalse();
    }

    @Test
    void customIgnoresReplaceDefaults() {
        RepositoryIgnoreRules custom = new RepositoryIgnoreRules(List.of("mybuild"));
        assertThat(custom.isIgnoredDirectoryName("target")).isFalse();
        assertThat(custom.isIgnoredDirectoryName("mybuild")).isTrue();
    }

    @Test
    void ignoresWorkingDirectoryMarker() {
        assertThat(rules.isIgnored(Path.of("node_modules", ".package-lock.json"))).isTrue();
    }
}
