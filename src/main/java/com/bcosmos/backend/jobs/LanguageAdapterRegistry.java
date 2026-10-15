package com.bcosmos.backend.jobs;

import com.bcosmos.core.language.LanguageAdapter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The registered language adapters (ADR 0012). Everything the pipeline needs about languages is asked here, so no
 * other class knows that phase 1 only speaks Java.
 */
public final class LanguageAdapterRegistry {

    private final List<LanguageAdapter<?>> adapters;
    private final Set<String> languageIds;

    public LanguageAdapterRegistry(List<LanguageAdapter<?>> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        this.adapters = List.copyOf(adapters);
        Set<String> ids = new LinkedHashSet<>();
        this.adapters.forEach(adapter -> ids.add(adapter.languageId()));
        this.languageIds = Set.copyOf(ids);
    }

    /** The adapter that handles this repository-relative path, if any. */
    public Optional<LanguageAdapter<?>> forFile(Path relativePath) {
        return adapters.stream().filter(adapter -> adapter.supports(relativePath)).findFirst();
    }

    /** The first registered adapter. Only a fallback: see {@link #dominantAdapter(List)}. */
    public LanguageAdapter<?> primaryAdapter() {
        if (adapters.isEmpty()) {
            throw new IllegalStateException("no language adapter is registered");
        }
        return adapters.getFirst();
    }

    /**
     * The adapter whose run-level metadata (parser version, analyzer version, rule set hash) describes a run: the one
     * that handled most of the files discovery found.
     *
     * <p>Until Phase 10 there was one adapter, and a run could record {@link #primaryAdapter()} before looking at
     * anything. With two registered, a TypeScript run recorded the Java toolchain, so the Quality screen showed
     * `javaparser` and `pmd-java` as the tools behind findings ESLint had produced (ADR 0033, ADR 0034). Ties keep
     * the first file's adapter, which is deterministic because discovery sorts paths. A run that saw no supported
     * file records the primary adapter, so the row still says which tools were on offer.</p>
     */
    public LanguageAdapter<?> dominantAdapter(List<Path> relativePaths) {
        Objects.requireNonNull(relativePaths, "relativePaths");
        Map<LanguageAdapter<?>, Long> filesByAdapter = new LinkedHashMap<>();
        for (Path relativePath : relativePaths) {
            forFile(relativePath).ifPresent(adapter -> filesByAdapter.merge(adapter, 1L, Long::sum));
        }
        LanguageAdapter<?> dominant = primaryAdapter();
        long mostFiles = 0;
        for (Map.Entry<LanguageAdapter<?>, Long> entry : filesByAdapter.entrySet()) {
            if (entry.getValue() > mostFiles) {
                dominant = entry.getKey();
                mostFiles = entry.getValue();
            }
        }
        return dominant;
    }

    public Set<String> languageIds() {
        return languageIds;
    }

    public List<LanguageAdapter<?>> adapters() {
        return adapters;
    }
}
