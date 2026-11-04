package com.bcosmos.core.pipeline;

import java.io.IOException;

/**
 * Source of file content for one analysis pass (spec v2 section 3.1, ADR 0025).
 *
 * <p>The interface separates "where to read a file from" from "how to analyse it" so the same
 * {@link CommitAnalysisPipeline} works in two contexts:
 * <ul>
 *   <li>The backend's in-process runner reads files from a local path (see {@code LocalFileSource}).
 *   <li>The worker reads BEFORE/AFTER blobs from a JGit repository without a checkout
 *       (see {@code JGitBlobSource} in {@code analysis-worker}).
 * </ul>
 *
 * <p>No implementation may execute code from the analysed repository (spec v2 section 0 rule 4).</p>
 */
public interface FileSource {

    /**
     * Returns the full source text for the given repository-relative path.
     *
     * @param relativePath repository-relative path, e.g. {@code src/main/java/com/acme/Foo.java}
     * @throws IOException when the file cannot be read
     */
    String read(String relativePath) throws IOException;
}
