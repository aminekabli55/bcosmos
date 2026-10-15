package com.bcosmos.backend.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.typescript.TypeScriptAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LanguageAdapterRegistryTest {

    private final LanguageAdapterRegistry registry = new LanguageAdapterRegistry(
            List.of(new JavaAdapter(), new TypeScriptAdapter()));

    @Test
    void registersBothJavaAndTypeScriptLanguages() {
        assertThat(registry.languageIds()).containsExactlyInAnyOrder("java", "typescript");
    }

    @Test
    void resolvesJavaFileToExpectedAdapter() {
        Optional<LanguageAdapter<?>> adapter = registry.forFile(Path.of("src/main/java/Foo.java"));
        assertThat(adapter).isPresent();
        assertThat(adapter.get().languageId()).isEqualTo("java");
    }

    @Test
    void resolvesTypeScriptFileToExpectedAdapter() {
        Optional<LanguageAdapter<?>> tsAdapter = registry.forFile(Path.of("src/app/book.service.ts"));
        assertThat(tsAdapter).isPresent();
        assertThat(tsAdapter.get().languageId()).isEqualTo("typescript");

        Optional<LanguageAdapter<?>> tsxAdapter = registry.forFile(Path.of("src/components/Button.tsx"));
        assertThat(tsxAdapter).isPresent();
        assertThat(tsxAdapter.get().languageId()).isEqualTo("typescript");
    }

    @Test
    void ignoresTypeScriptDefinitionFiles() {
        Optional<LanguageAdapter<?>> dtsAdapter = registry.forFile(Path.of("src/types/index.d.ts"));
        assertThat(dtsAdapter).isEmpty();
    }

    @Test
    void runMetadataComesFromTheAdapterThatHandledMostFiles() {
        assertThat(registry.dominantAdapter(List.of(
                Path.of("src/app/book.service.ts"), Path.of("src/app/book.model.ts"))).languageId())
                .isEqualTo("typescript");
        assertThat(registry.dominantAdapter(List.of(
                Path.of("src/main/java/Foo.java"), Path.of("src/main/java/Bar.java"),
                Path.of("src/app/book.service.ts"))).languageId())
                .isEqualTo("java");
    }

    @Test
    void aRunWithNoSupportedFileFallsBackToThePrimaryAdapter() {
        assertThat(registry.dominantAdapter(List.of(Path.of("README.md"), Path.of("build.gradle.kts"))).languageId())
                .isEqualTo("java");
        assertThat(registry.dominantAdapter(List.of()).languageId()).isEqualTo("java");
    }

    @Test
    void returnsEmptyForUnsupportedExtension() {
        Optional<LanguageAdapter<?>> pyAdapter = registry.forFile(Path.of("scripts/run.py"));
        assertThat(pyAdapter).isEmpty();
    }
}
