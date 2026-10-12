package com.bcosmos.backend.jobs;

import com.bcosmos.core.language.LanguageAdapter;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
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

    /**
     * The adapter whose run-level metadata (parser version, analyzer version, rule set hash) describes a run.
     * With a single registered language this is simply that language; per-language run metadata arrives with the
     * second adapter (phase 10).
     */
    public LanguageAdapter<?> primaryAdapter() {
        if (adapters.isEmpty()) {
            throw new IllegalStateException("no language adapter is registered");
        }
        return adapters.getFirst();
    }

    public Set<String> languageIds() {
        return languageIds;
    }

    public List<LanguageAdapter<?>> adapters() {
        return adapters;
    }
}
