package com.bcosmos.miner.git;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Walks a Git repository's commit history and extracts commits with changed Java files (spec v2 section 5.2).
 */
@Component
public class CommitWalker {

    private static final Logger log = LoggerFactory.getLogger(CommitWalker.class);

    public List<CommitDiscoveredPayload> walkCommits(Repository repository, int commitWindow) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            log.warn("Repository has no HEAD commit to walk");
            return List.of();
        }

        List<CommitDiscoveredPayload> results = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            revWalk.markStart(revWalk.parseCommit(head));
            int count = 0;

            for (RevCommit commit : revWalk) {
                if (count >= commitWindow) {
                    break;
                }
                count++;

                List<String> changedJavaFiles = extractChangedJavaFiles(repository, revWalk, diffFormatter, commit);
                if (!changedJavaFiles.isEmpty()) {
                    List<String> parentHashes = Arrays.stream(commit.getParents())
                            .map(RevCommit::getName)
                            .toList();

                    Instant committedAt = Instant.ofEpochSecond(commit.getCommitTime());
                    results.add(new CommitDiscoveredPayload(
                            commit.getName(),
                            parentHashes,
                            committedAt,
                            changedJavaFiles));
                }
            }
        }

        return results;
    }

    private List<String> extractChangedJavaFiles(
            Repository repository,
            RevWalk revWalk,
            DiffFormatter diffFormatter,
            RevCommit commit) throws IOException {

        List<String> files = new ArrayList<>();

        if (commit.getParentCount() == 0) {
            // Root commit: all Java files in the tree are newly added
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (path.endsWith(".java")) {
                        files.add(path);
                    }
                }
            }
        } else {
            RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
            List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                String path = diff.getNewPath();
                if (path == null || path.equals(DiffEntry.DEV_NULL)) {
                    path = diff.getOldPath();
                }
                if (path != null && path.endsWith(".java") && !path.equals(DiffEntry.DEV_NULL)) {
                    files.add(path);
                }
            }
        }

        return files;
    }
}
