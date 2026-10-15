package com.bcosmos.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.jobs.LanguageAdapterRegistry;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.typescript.TypeScriptAdapter;
import com.bcosmos.core.typescript.TypeScriptParsedFile;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Guards the failure mode of ADR 0036: if the tree-sitter binding is missing from <em>this</em> module's runtime
 * classpath, Spring fails while creating the {@code typeScriptAdapter} bean —
 * {@code Factory method 'typeScriptAdapter' threw exception ... org/treesitter/TSLanguage} — and every bean that
 * depends on {@link LanguageAdapterRegistry} (PatchValidator, SuggestionService, ModelCompareService) fails with it.
 *
 * <p>The binding is therefore asserted here through the real bean factory method and through one real parse, not by
 * checking the pom: a classpath assembled by an IDE, a container or a stale module model is exactly what broke.</p>
 */
@DisplayName("AnalysisConfiguration exposes the TypeScript adapter with its parser binding present")
class AnalysisConfigurationTypeScriptBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AnalysisConfiguration.class)
            .withPropertyValues("bcosmos.repository.allowed-roots=");

    @Test
    void buildsTheTypeScriptAdapterThroughTheBeanFactory() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(TypeScriptAdapter.class).isNotNull();
        });
    }

    @Test
    void registrySeesBothLanguages() {
        runner.run(context -> {
            LanguageAdapterRegistry registry = context.getBean(LanguageAdapterRegistry.class);
            assertThat(registry.languageIds()).containsExactlyInAnyOrder("java", "typescript");
        });
    }

    /** The binding must be usable, not merely present: this loads the native tree-sitter library too. */
    @Test
    void parsesTypeScriptWithTheBinding() {
        runner.run(context -> {
            LanguageAdapter<TypeScriptParsedFile> adapter = context.getBean(TypeScriptAdapter.class);

            TypeScriptParsedFile parsed = adapter.parse(
                    Path.of("src/app/book.service.ts"), "export class BookService {\n  find(id: number) { return id; }\n}\n");

            assertThat(parsed.parsed()).isTrue();
            assertThat(parsed.symbols()).isNotEmpty();
        });
    }
}
