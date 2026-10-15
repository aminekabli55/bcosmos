package com.bcosmos.miner.git;

import com.bcosmos.miner.config.MinerProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages local clones of public repositories using JGit without checkout (ADR 0011).
 */
@Component
public class JGitCloneCache {

    private static final Logger log = LoggerFactory.getLogger(JGitCloneCache.class);

    private final MinerProperties properties;

    public JGitCloneCache(MinerProperties properties) {
        this.properties = properties;
    }

    public Repository openOrClone(Long repositoryId, String remoteUrl) throws GitAPIException, IOException {
        Path base = Path.of(properties.cloneDir());
        Files.createDirectories(base);

        Path repoDir = base.resolve(String.valueOf(repositoryId));
        File gitDir = repoDir.resolve(".git").toFile();

        if (gitDir.exists()) {
            log.info("Repository {} already exists in cache, fetching latest refs", repositoryId);
            try (Git git = Git.open(repoDir.toFile())) {
                git.fetch().call();
                return Git.open(repoDir.toFile()).getRepository();
            } catch (Exception e) {
                log.warn("Fetch failed for repo {}, attempting to re-use existing local refs: {}", repositoryId, e.getMessage());
                return Git.open(repoDir.toFile()).getRepository();
            }
        }

        log.info("Cloning {} to {} (no-checkout)", remoteUrl, repoDir);
        Git git = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(repoDir.toFile())
                .setNoCheckout(true)
                .call();

        return git.getRepository();
    }
}
