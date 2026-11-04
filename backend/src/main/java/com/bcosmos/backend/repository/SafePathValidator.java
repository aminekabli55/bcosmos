package com.bcosmos.backend.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a user-supplied path into a canonical path that is guaranteed to live inside an allowed root
 * (spec v2 section 10: "canonicalize paths, restrict to user-approved roots, never follow symlinks outside the
 * root, never run repo scripts").
 *
 * <p>Every check happens on the <em>real</em> path ({@link Path#toRealPath}), so a symlink pointing outside an
 * allowed root is rejected even though the link itself sits inside one. The validator never reads file content and
 * never executes anything from the repository.</p>
 */
public final class SafePathValidator {

    private final List<Path> allowedRoots;

    /**
     * @param allowedRoots configured roots; they are canonicalized once, and an entry that does not exist yet is
     *                     kept as an absolute normalized path so a later start can still match it
     */
    public SafePathValidator(List<String> allowedRoots) {
        Objects.requireNonNull(allowedRoots, "allowedRoots");
        List<Path> canonical = new ArrayList<>();
        for (String root : allowedRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            canonical.add(canonicalizeRoot(root));
        }
        this.allowedRoots = List.copyOf(canonical);
    }

    /** True when at least one allowed root is configured; without one, nothing can be registered. */
    public boolean hasAllowedRoots() {
        return !allowedRoots.isEmpty();
    }

    /** Number of configured allowed roots (used in tests and diagnostics; roots themselves are never logged). */
    public int allowedRootCount() {
        return allowedRoots.size();
    }

    /**
     * Validates a candidate repository path.
     *
     * @return the canonical, symlink-resolved directory
     * @throws InvalidRepositoryPathException when the path is blank, malformed, missing, not a directory, or outside
     *                                        every allowed root
     */
    public Path validateRepositoryPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new InvalidRepositoryPathException("a repository path is required");
        }
        if (!hasAllowedRoots()) {
            throw new InvalidRepositoryPathException(
                    "no allowed roots are configured, so no repository path can be accepted");
        }

        Path path;
        try {
            path = Path.of(candidate.trim());
        } catch (InvalidPathException e) {
            throw new InvalidRepositoryPathException("the repository path is not a valid path", e);
        }

        Path absolute = path.isAbsolute() ? path : path.toAbsolutePath();
        if (!Files.exists(absolute)) {
            throw new InvalidRepositoryPathException("the repository path does not exist");
        }
        if (!Files.isDirectory(absolute)) {
            throw new InvalidRepositoryPathException("the repository path is not a directory");
        }

        Path real;
        try {
            real = absolute.toRealPath();
        } catch (IOException e) {
            throw new InvalidRepositoryPathException("the repository path cannot be resolved", e);
        }

        if (!isInsideAllowedRoot(real)) {
            throw new InvalidRepositoryPathException(
                    "the repository path is outside the allowed roots configured for this installation");
        }
        return real;
    }

    /** True when the canonical path is inside one of the allowed roots (the root itself counts as inside). */
    public boolean isInsideAllowedRoot(Path canonicalPath) {
        for (Path root : allowedRoots) {
            if (canonicalPath.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static Path canonicalizeRoot(String root) {
        Path path;
        try {
            path = Path.of(root.trim());
        } catch (InvalidPathException e) {
            throw new InvalidRepositoryPathException("an allowed root is not a valid path", e);
        }
        Path absolute = path.isAbsolute() ? path : path.toAbsolutePath();
        try {
            return Files.exists(absolute) ? absolute.toRealPath() : absolute.normalize();
        } catch (IOException e) {
            return absolute.normalize();
        }
    }
}
