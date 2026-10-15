package com.bcosmos.worker.consumer;

import com.bcosmos.core.pipeline.FileSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FileSource implementation that reads files from a local directory (spec v2 section 3.1, ADR 0025).
 */
public class LocalFileSource implements FileSource {

    private final Path root;

    public LocalFileSource(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String read(String relativePath) throws IOException {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("path traversal detected: " + relativePath);
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }
}
