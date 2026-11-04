package com.bcosmos.miner.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CommitWalker unit tests")
class CommitWalkerTest {

    @TempDir
    Path tempDir;

    private final CommitWalker walker = new CommitWalker();

    @Test
    void extractsCommitsWithChangedJavaFiles() throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Repository repo = git.getRepository();

            // Commit 1: add A.java
            Path fileA = tempDir.resolve("A.java");
            Files.writeString(fileA, "public class A {}");
            git.add().addFilepattern("A.java").call();
            RevCommit c1 = git.commit().setMessage("Add A").call();

            // Commit 2: modify A.java and add text.txt (non-Java)
            Files.writeString(fileA, "public class A { int x; }");
            Path text = tempDir.resolve("text.txt");
            Files.writeString(text, "notes");
            git.add().addFilepattern("A.java").addFilepattern("text.txt").call();
            RevCommit c2 = git.commit().setMessage("Update A and add text").call();

            // Commit 3: only modify text.txt (no Java changes)
            Files.writeString(text, "notes updated");
            git.add().addFilepattern("text.txt").call();
            git.commit().setMessage("Update text only").call();

            List<CommitDiscoveredPayload> discovered = walker.walkCommits(repo, 10);

            // Commit 3 had no Java changes, so only c2 and c1 should be discovered
            assertThat(discovered).hasSize(2);

            CommitDiscoveredPayload newest = discovered.get(0);
            assertThat(newest.commitHash()).isEqualTo(c2.getName());
            assertThat(newest.parentHashes()).containsExactly(c1.getName());
            assertThat(newest.changedJavaFiles()).containsExactly("A.java");

            CommitDiscoveredPayload root = discovered.get(1);
            assertThat(root.commitHash()).isEqualTo(c1.getName());
            assertThat(root.parentHashes()).isEmpty();
            assertThat(root.changedJavaFiles()).containsExactly("A.java");
        }
    }
}
