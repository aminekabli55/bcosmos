package com.bcosmos.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SafePathValidatorTest {

    private final Path tempRoot = createTempRoot();

    @Test
    void rejectsBlankPath() {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        assertThatThrownBy(() -> validator.validateRepositoryPath("   "))
                .isInstanceOf(InvalidRepositoryPathException.class)
                .hasMessageContaining("a repository path is required");
    }

    @Test
    void rejectsPathOutsideAllowedRoot() throws IOException {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        Path outside = createTempDirectory("outside");
        try {
            assertThatThrownBy(() -> validator.validateRepositoryPath(outside.toString()))
                    .isInstanceOf(InvalidRepositoryPathException.class)
                    .hasMessageContaining("outside the allowed roots");
        } finally {
            delete(outside);
        }
    }

    @Test
    void acceptsPathInsideAllowedRoot() throws IOException {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        Path under = tempRoot.resolve("repo");
        Files.createDirectories(under);
        Path canonical = validator.validateRepositoryPath(under.toString());
        assertThat(canonical).isEqualTo(under.toRealPath());
    }

    @Test
    void acceptsPathWhenRepositoryItselfIsAllowedRoot() throws IOException {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        Path canonical = validator.validateRepositoryPath(tempRoot.toString());
        assertThat(canonical).isEqualTo(tempRoot.toRealPath());
    }

    @Test
    void rejectsNonExistentPath() {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        assertThatThrownBy(() -> validator.validateRepositoryPath(tempRoot.resolve("does-not-exist").toString()))
                .isInstanceOf(InvalidRepositoryPathException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsFileInsteadOfDirectory() throws IOException {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        Path file = tempRoot.resolve("a-file.txt");
        Files.createFile(file);
        try {
            assertThatThrownBy(() -> validator.validateRepositoryPath(file.toString()))
                    .isInstanceOf(InvalidRepositoryPathException.class)
                    .hasMessageContaining("not a directory");
        } finally {
            delete(file);
        }
    }

    @Test
    void rejectsWhenNoAllowedRootsAreConfigured() {
        SafePathValidator validator = new SafePathValidator(List.of());
        assertThatThrownBy(() -> validator.validateRepositoryPath("/any"))
                .isInstanceOf(InvalidRepositoryPathException.class)
                .hasMessageContaining("no allowed roots are configured");
    }

    @Test
    void rejectsMalformedPath() {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        assertThatThrownBy(() -> validator.validateRepositoryPath(""))
                .isInstanceOf(InvalidRepositoryPathException.class);
    }

    @Test
    void isInsideAllowedRootMatchesRootItself() throws IOException {
        SafePathValidator validator = new SafePathValidator(List.of(tempRoot.toString()));
        assertThat(validator.isInsideAllowedRoot(tempRoot.toRealPath())).isTrue();
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("validator-root");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best-effort cleanup in tests
        }
    }
}
