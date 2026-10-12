package com.bcosmos.backend.repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Directories that are never walked (spec v2 section 12, Phase 1: "file discovery with ignore rules
 * ({@code .git}, {@code target}, {@code build}, {@code node_modules}) and limits").
 *
 * <p>A rule matches any path segment, so {@code target} is ignored at every depth. The list can be replaced from
 * configuration; discovery never follows symlinks, so an ignored directory cannot be re-entered through a link.</p>
 */
public final class RepositoryIgnoreRules {

    /** Default ignores: version control, build output, IDE state, dependency trees and virtualenvs. */
    public static final List<String> DEFAULT_IGNORED_DIRECTORIES = List.of(
            ".git", ".svn", ".hg",
            "target", "build", "out", "bin", "dist",
            "node_modules",
            ".idea", ".vscode", ".settings", ".gradle",
            "coverage",
            ".venv", "venv", "__pycache__");

    private final Set<String> ignoredDirectoryNames;

    public RepositoryIgnoreRules(List<String> ignoredDirectories) {
        List<String> effective = ignoredDirectories == null || ignoredDirectories.isEmpty()
                ? DEFAULT_IGNORED_DIRECTORIES
                : ignoredDirectories;
        this.ignoredDirectoryNames = effective.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True when any segment of the relative path is an ignored directory name. */
    public boolean isIgnored(Path relativePath) {
        for (Path segment : relativePath) {
            if (ignoredDirectoryNames.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** True when the directory named {@code directoryName} must not be entered. */
    public boolean isIgnoredDirectoryName(String directoryName) {
        return directoryName != null && ignoredDirectoryNames.contains(directoryName.toLowerCase(Locale.ROOT));
    }

    public Set<String> ignoredDirectoryNames() {
        return ignoredDirectoryNames;
    }
}
