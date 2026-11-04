package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileDiscoveryTest {

    @Test
    void walksOnlyAnalysableJavaFiles() throws IOException {
        Path root = Files.createTempDirectory("discovery");
        Path sourceDir = root.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Service.java"), "package com.example;\npublic class Service {}\n");
        Files.writeString(sourceDir.resolve("Target.java"), "package com.example;\npublic class Target {}\n");

        // large file
        Path largeFile = root.resolve("large.java");
        Files.writeString(largeFile, "x".repeat(100_000));

        // ignored dir with real contents (so walkFileTree sees a failed visit)
        Path ignoredDir = root.resolve("target");
        Files.createDirectories(ignoredDir.resolve("classes"));
        Files.writeString(ignoredDir.resolve("classes/Generated.class"), "bytecode");

        FileDiscovery discovery = fileDiscovery(root, 10, 1_000);

        DiscoveryResult result = discovery.discover(root);

        assertThat(result.discoveredCount()).isEqualTo(2);
        assertThat(result.fileLimitReached()).isFalse();
        assertThat(result.files()).extracting(DiscoveredFile::relativePathString)
                .containsExactlyInAnyOrder(
                        "src/main/java/com/example/Service.java",
                        "src/main/java/com/example/Target.java");
        assertThat(result.skipped()).extracting(SkippedFile::relativePath)
                .contains("large.java");
    }

    @Test
    void skipsFilesLargerThanLimit() throws IOException {
        Path root = Files.createTempDirectory("discovery");
        Path sourceDir = root.resolve("src/main/java");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Small.java"), "package com.example;\npublic class Small {}\n");
        Path largeFile = sourceDir.resolve("Large.java");
        Files.writeString(largeFile, "x".repeat(50_000));

        FileDiscovery discovery = fileDiscovery(root, 10, 1_000);
        DiscoveryResult result = discovery.discover(root);

        assertThat(result.discoveredCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.files()).extracting(DiscoveredFile::relativePathString)
                .containsExactly("src/main/java/Small.java");
    }

    @Test
    void stopsAtFileLimit() throws IOException {
        Path root = Files.createTempDirectory("discovery");
        Path sourceDir = root.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDir);
        for (int i = 0; i < 6; i++) {
            Files.writeString(sourceDir.resolve("File" + i + ".java"), "package com.example;\npublic class File" + i + " {}\n");
        }

        FileDiscovery discovery = fileDiscovery(root, 4, 1_000_000);
        DiscoveryResult result = discovery.discover(root);

        assertThat(result.discoveredCount()).isEqualTo(4);
        assertThat(result.fileLimitReached()).isTrue();
    }

    @Test
    void canonicalisesPathsOnWindowsStyleSeparators() throws IOException {
        Path root = Files.createTempDirectory("discovery");
        Path sourceDir = root.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("Main.java"), "package com.example;\npublic class Main {}\n");

        FileDiscovery discovery = fileDiscovery(root, 10, 1_000_000);
        DiscoveryResult result = discovery.discover(root);

        assertThat(result.files()).extracting(DiscoveredFile::relativePathString)
                .containsExactly("src/main/java/Main.java");
    }

    @Test
    void ignoresSymlinkedIgnoredDirectoryWhenSupported() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(supportsSymlinks(), "symlinks not supported on this filesystem");
        Path root = Files.createTempDirectory("discovery");
        Path realTarget = Files.createTempDirectory("real-target");
        try {
            Path link = root.resolve("target");
            Files.createSymbolicLink(link, realTarget);
            Path sourceDir = root.resolve("src/main/java");
            Files.createDirectories(sourceDir);
            Files.writeString(sourceDir.resolve("Real.java"), "package com.example;\npublic class Real {}\n");

            FileDiscovery discovery = fileDiscovery(root, 10, 1_000_000);
            DiscoveryResult result = discovery.discover(root);

            assertThat(result.discoveredCount()).isEqualTo(1);
            assertThat(result.files()).extracting(DiscoveredFile::relativePathString)
                    .containsExactly("src/main/java/Real.java");
            // The symlink target's contents must not be walked even though the link itself is inside the root.
            assertThat(result.files()).noneMatch(f -> f.relativePathString().contains("real-target"));
        } finally {
            deleteIfExists(realTarget);
        }
    }

    private static boolean supportsSymlinks() {
        try {
            Path root = Files.createTempDirectory("symlink-check");
            Path link = root.resolve("link");
            Files.createSymbolicLink(link, root);
            deleteIfExists(link);
            deleteIfExists(root);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static FileDiscovery fileDiscovery(Path root, int maxFiles, long maxFileSizeBytes) {
        RepositoryIgnoreRules ignoreRules = new RepositoryIgnoreRules(List.of());
        return new FileDiscovery(maxFiles, maxFileSizeBytes, ignoreRules,
                relative -> relative.toString().endsWith(".java"));
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best-effort cleanup
        }
    }
}
