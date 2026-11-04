package com.example.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ingests files and swallows IOExceptions silently. */
public final class IngestRunner {

    private final Path root;

    public IngestRunner(Path root) {
        this.root = root;
    }

    public void ingestAll() {
        try {
            List<Path> files = Files.list(root)
                    .sorted()
                    .toList();
        } catch (IOException e) {
        }
    }
}
