package com.bcosmos.worker.consumer;

import com.bcosmos.core.pipeline.FileSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * FileSource implementation that reads blobs directly from Git objects without checkout (ADR 0011, ADR 0025).
 */
public class JGitBlobSource implements FileSource {

    private final Repository repository;
    private final String commitHash;

    public JGitBlobSource(Repository repository, String commitHash) {
        this.repository = repository;
        this.commitHash = commitHash;
    }

    @Override
    public String read(String relativePath) throws IOException {
        String normalizedPath = relativePath.replace('\\', '/');
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        ObjectId commitId = repository.resolve(commitHash);
        if (commitId == null) {
            throw new FileNotFoundException("commit not found: " + commitHash);
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(normalizedPath));
                if (!treeWalk.next()) {
                    throw new FileNotFoundException("file not found in commit " + commitHash + ": " + normalizedPath);
                }
                ObjectId blobId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(blobId);
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
