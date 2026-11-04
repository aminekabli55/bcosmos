package com.bcosmos.backend.config;

import com.bcosmos.backend.jobs.LanguageAdapterRegistry;
import com.bcosmos.backend.repository.FileDiscovery;
import com.bcosmos.backend.repository.RepositoryIgnoreRules;
import com.bcosmos.backend.repository.SafePathValidator;
import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.typescript.TypeScriptAdapter;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the language-independent pipeline to the language adapter (ADR 0012) and to the configured limits.
 *
 * <p>{@link JavaAdapter} was the initial adapter of phase 1; {@link TypeScriptAdapter} was added in phase 10 (spec v2 section 3.1 &amp; 12).</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BcosmosProperties.class)
public class AnalysisConfiguration {

    /** Name of the executor used for in-process analysis jobs; phase 4 replaces the dispatcher, not this bean. */
    public static final String ANALYSIS_EXECUTOR = "analysisExecutor";

    @Bean
    public JavaAdapter javaAdapter() {
        return new JavaAdapter();
    }

    @Bean
    public TypeScriptAdapter typeScriptAdapter() {
        return new TypeScriptAdapter();
    }

    @Bean
    public LanguageAdapterRegistry languageAdapterRegistry(List<LanguageAdapter<?>> adapters) {
        return new LanguageAdapterRegistry(adapters);
    }

    @Bean
    public RepositoryIgnoreRules repositoryIgnoreRules(BcosmosProperties properties) {
        return new RepositoryIgnoreRules(properties.repository().ignoredDirectories());
    }

    @Bean
    public SafePathValidator safePathValidator(BcosmosProperties properties) {
        return new SafePathValidator(properties.repository().allowedRoots());
    }

    /**
     * Discovery for one run. The predicate comes from the registered adapters, so discovery and analysis can never
     * disagree about what is analysable (spec v2 section 12, Phase 1: ignore rules and limits).
     */
    @Bean
    public FileDiscovery fileDiscovery(List<LanguageAdapter<?>> adapters, RepositoryIgnoreRules ignoreRules,
            BcosmosProperties properties) {
        return new FileDiscovery(
                properties.repository().limits().maxFiles(),
                properties.repository().limits().maxFileSizeBytes(),
                ignoreRules,
                relative -> adapters.stream().anyMatch(adapter -> adapter.supports(relative)));
    }

    /**
     * Executor for in-process analysis runs. A single worker keeps phase 1 deterministic and stops one laptop from
     * running several large repositories at once; the queue is a hard bound on accepted work.
     */
    @Bean(name = ANALYSIS_EXECUTOR)
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("bcosmos-analysis-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
