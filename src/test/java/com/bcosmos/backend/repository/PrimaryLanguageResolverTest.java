package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Primary language resolver (ADR 0033): a repository is what its files are")
class PrimaryLanguageResolverTest {

    @Test
    void the_language_with_the_most_files_wins() {
        assertThat(PrimaryLanguageResolver.resolve("java", Map.of("typescript", 4L, "java", 1L)))
                .isEqualTo("typescript");
    }

    @Test
    void a_java_repository_stays_java() {
        assertThat(PrimaryLanguageResolver.resolve("java", Map.of("java", 6L, "typescript", 2L)))
                .isEqualTo("java");
    }

    @Test
    void a_tie_is_broken_alphabetically_so_two_runs_agree() {
        Map<String, Long> census = new LinkedHashMap<>();
        census.put("typescript", 3L);
        census.put("java", 3L);
        assertThat(PrimaryLanguageResolver.resolve(null, census)).isEqualTo("java");
        assertThat(PrimaryLanguageResolver.resolve(null, new LinkedHashMap<>(Map.of("java", 3L, "typescript", 3L))))
                .isEqualTo("java");
    }

    @Test
    void an_empty_run_keeps_the_stored_value_instead_of_blanking_it() {
        assertThat(PrimaryLanguageResolver.resolve("java", Map.of())).isEqualTo("java");
        assertThat(PrimaryLanguageResolver.resolve("typescript", Map.of("", 5L))).isEqualTo("typescript");
        assertThat(PrimaryLanguageResolver.resolve("java", Map.of("typescript", 0L))).isEqualTo("java");
    }

    @Test
    void a_missing_current_value_is_allowed() {
        assertThat(PrimaryLanguageResolver.resolve(null, Map.of("typescript", 1L))).isEqualTo("typescript");
        assertThat(PrimaryLanguageResolver.resolve(null, Map.of())).isNull();
    }
}
