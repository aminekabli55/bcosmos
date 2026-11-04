package com.bcosmos.backend.repository;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Walks a repository and returns the files worth analysing (spec v2 section 12, Phase 1).
 *
 * <p>Guarantees: symlinks are never followed ({@link Files#walkFileTree} without
 * {@link java.nio.file.FileVisitOption#FOLLOW_LINKS}), ignored directories are skipped before they are entered, the
 * file count and the per-file size are limited, and nothing but names and sizes is read. Analysed repositories are
 * untrusted input: this class only ever lists and reads files, never runs them (spec section 0 rule 4).</p>
 */
public final class FileDiscovery {

    private final int maxFiles;
    private final long maxFileSizeBytes;
    private final RepositoryIgnoreRules ignoreRules;
    private final Predicate<Path> analyzable;

    /**
     * @param maxFiles         hard limit on the number of files one run will analyse
     * @param maxFileSizeBytes files larger than this are skipped with a reason
     * @param ignoreRules      directories that are never walked
     * @param analyzable       predicate that decides which files are candidates (extension check per language)
     */
    public FileDiscovery(int maxFiles, long maxFileSizeBytes, RepositoryIgnoreRules ignoreRules,
            Predicate<Path> analyzable) {
        if (maxFiles < 1) {
            throw new IllegalArgumentException("maxFiles must be at least 1");
        }
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException("maxFileSizeBytes must be at least 1");
        }
        this.maxFiles = maxFiles;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.ignoreRules = Objects.requireNonNull(ignoreRules, "ignoreRules");
        this.analyzable = Objects.requireNonNull(analyzable, "analyzable");
    }

    public DiscoveryResult discover(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        List<DiscoveredFile> files = new ArrayList<>();
        List<SkippedFile> skipped = new ArrayList<>();
        boolean[] limitReached = {false};

        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (!dir.equals(root) && ignoreRules.isIgnoredDirectoryName(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                Path relative = root.relativize(file);
                if (!analyzeableRelativePath(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                if (ignoreRules.isIgnored(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.size() > maxFileSizeBytes) {
                    skipped.add(new SkippedFile(toRelativeString(relative),
                            "larger than the configured file size limit"));
                    return FileVisitResult.CONTINUE;
                }
                if (files.size() >= maxFiles) {
                    limitReached[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                files.add(new DiscoveredFile(relative, file, attributes.size()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                skipped.add(new SkippedFile(toRelativeString(root.relativize(file)),
                        "could not be read: " + exception.getClass().getSimpleName()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) {
                return exception == null ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
            }
        });

        files.sort(Comparator.comparing(DiscoveredFile::relativePathString));
        skipped.sort(Comparator.comparing(SkippedFile::relativePath));
        return new DiscoveryResult(files, skipped, limitReached[0]);
    }

    private boolean analyzeableRelativePath(Path relative) {
        return analyzable.test(relative);
    }

    private static String toRelativeString(Path relative) {
        return relative.toString().replace('\\', '/');
    }
}
