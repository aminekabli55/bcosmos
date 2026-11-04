package com.bcosmos.backend.ai.rag;

import java.util.Objects;

/**
 * One chunk as the chunker produced it, before it has a vector or a row id.
 *
 * @param symbolId      symbol this chunk is the declaration of
 * @param fileId        file record it came from
 * @param relativePath  repository-relative path, copied so a citation needs no join
 * @param qualifiedName fully qualified symbol name
 * @param startLine     first line of the declaration in the file
 * @param endLine       last line of the declaration in the file
 * @param text          header plus code, exactly what is embedded and later cited
 * @param tokenCount    estimated tokens of {@link #text()}
 * @param truncated     whether the declaration was cut to fit the chunk limit
 */
public record CodeChunkDraft(
        long symbolId,
        long fileId,
        String relativePath,
        String qualifiedName,
        int startLine,
        int endLine,
        String text,
        int tokenCount,
        boolean truncated) {

    public CodeChunkDraft {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(text, "text");
    }
}
