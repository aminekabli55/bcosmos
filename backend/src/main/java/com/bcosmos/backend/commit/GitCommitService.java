package com.bcosmos.backend.commit;

import com.bcosmos.backend.persistence.CommitRecordEntity;
import com.bcosmos.backend.persistence.CommitRecordJpaRepository;
import com.bcosmos.backend.persistence.RepositoryEntity;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GitCommitService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitService.class);
    private static final int MAX_DIFF_CHARS = 25000;

    private final CommitRecordJpaRepository commitRecords;

    public GitCommitService(CommitRecordJpaRepository commitRecords) {
        this.commitRecords = commitRecords;
    }

    public record CommitSummary(
            String hash,
            String shortHash,
            String message,
            String author,
            Instant committedAt,
            List<String> parentHashes) {
    }

    public record DiffResult(
            String fromHash,
            String toHash,
            List<FileDiffEntry> files,
            boolean hasMainChanges,
            boolean hasTestChanges,
            String unifiedDiff) {
    }

    public record FileDiffEntry(
            String oldPath,
            String newPath,
            String changeType) {
    }

    /**
     * Lists recent commits for a repository. If local Git repository exists on disk, uses JGit;
     * otherwise falls back to mined commit records.
     */
    public List<CommitSummary> listCommits(RepositoryEntity repo, int limit) {
        String path = repo.getCanonicalLocalPath();
        if (path != null) {
            File gitDir = new File(path, ".git");
            if (!gitDir.exists()) {
                gitDir = new File(path); // in case repo path is the .git directory or bare repo
            }
            if (gitDir.exists()) {
                try (Git git = Git.open(gitDir)) {
                    List<CommitSummary> summaries = new ArrayList<>();
                    Iterable<RevCommit> commits = git.log().setMaxCount(limit).call();
                    for (RevCommit commit : commits) {
                        List<String> parents = new ArrayList<>();
                        for (RevCommit p : commit.getParents()) {
                            parents.add(p.getName());
                        }
                        String shortHash = commit.getName().substring(0, Math.min(7, commit.getName().length()));
                        summaries.add(new CommitSummary(
                                commit.getName(),
                                shortHash,
                                commit.getShortMessage(),
                                commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "anonymous",
                                Instant.ofEpochSecond(commit.getCommitTime()),
                                parents));
                    }
                    return summaries;
                } catch (Exception e) {
                    log.warn("Failed to read git commits from path {}: {}", path, e.getMessage());
                }
            }
        }

        // Fallback to commit_record database table
        List<CommitRecordEntity> records = commitRecords.findTop20ByRepositoryIdOrderByCommittedAtDesc(repo.getId());
        return records.stream()
                .limit(limit)
                .map(r -> new CommitSummary(
                        r.getHash(),
                        r.getHash().substring(0, Math.min(7, r.getHash().length())),
                        "Commit " + r.getHash().substring(0, Math.min(7, r.getHash().length())),
                        r.getAuthorHash() != null ? r.getAuthorHash().substring(0, Math.min(8, r.getAuthorHash().length())) : "miner",
                        r.getCommittedAt(),
                        r.getParentHashes() != null ? java.util.Arrays.asList(r.getParentHashes()) : List.of()))
                .toList();
    }

    /**
     * Computes the diff between two commits for a repository.
     */
    public DiffResult computeDiff(RepositoryEntity repo, String fromCommitHash, String toCommitHash) {
        String path = repo.getCanonicalLocalPath();
        if (path != null) {
            File gitDir = new File(path, ".git");
            if (!gitDir.exists()) {
                gitDir = new File(path);
            }
            if (gitDir.exists()) {
                try (Git git = Git.open(gitDir)) {
                    Repository repository = git.getRepository();
                    ObjectId fromId = repository.resolve(fromCommitHash + "^{tree}");
                    ObjectId toId = repository.resolve(toCommitHash + "^{tree}");

                    if (fromId != null && toId != null) {
                        try (ObjectReader reader = repository.newObjectReader()) {
                            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                            oldTreeIter.reset(reader, fromId);
                            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                            newTreeIter.reset(reader, toId);

                            List<DiffEntry> diffs;
                            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                                df.setRepository(repository);
                                df.setDiffComparator(RawTextComparator.DEFAULT);
                                df.setDetectRenames(true);
                                diffs = df.scan(oldTreeIter, newTreeIter);
                            }

                            List<FileDiffEntry> fileEntries = new ArrayList<>();
                            boolean hasMain = false;
                            boolean hasTest = false;

                            for (DiffEntry diff : diffs) {
                                String oldP = diff.getOldPath();
                                String newP = diff.getNewPath();
                                fileEntries.add(new FileDiffEntry(oldP, newP, diff.getChangeType().name()));

                                String checkP = newP != null && !newP.equals("/dev/null") ? newP : oldP;
                                if (checkP != null) {
                                    if (checkP.contains("src/main/") || (!checkP.contains("src/test/") && checkP.endsWith(".java"))) {
                                        hasMain = true;
                                    }
                                    if (checkP.contains("src/test/") || checkP.endsWith("Test.java") || checkP.endsWith("Spec.java")) {
                                        hasTest = true;
                                    }
                                }
                            }

                            // Capture text unified diff
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try (DiffFormatter textDf = new DiffFormatter(out)) {
                                textDf.setRepository(repository);
                                textDf.setDiffComparator(RawTextComparator.DEFAULT);
                                for (DiffEntry diff : diffs) {
                                    textDf.format(diff);
                                    if (out.size() >= MAX_DIFF_CHARS) {
                                        break;
                                    }
                                }
                            }
                            String unifiedDiff = out.toString(StandardCharsets.UTF_8);
                            if (unifiedDiff.length() > MAX_DIFF_CHARS) {
                                unifiedDiff = unifiedDiff.substring(0, MAX_DIFF_CHARS) + "\n... [diff truncated for length]";
                            }

                            return new DiffResult(fromCommitHash, toCommitHash, fileEntries, hasMain, hasTest, unifiedDiff);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to compute git diff between {} and {}: {}", fromCommitHash, toCommitHash, e.getMessage());
                }
            }
        }

        return new DiffResult(fromCommitHash, toCommitHash, Collections.emptyList(), false, false, "");
    }
}
