package com.bcosmos.worker.consumer;

import com.bcosmos.core.events.CommitDiscoveredPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.FindingPayload;
import com.bcosmos.core.events.FindingsPayload;
import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.pipeline.CommitAnalysisPipeline;
import com.bcosmos.core.pipeline.CommitAnalysisResult;
import com.bcosmos.worker.config.WorkerProperties;
import com.bcosmos.worker.events.FindingsPublisher;
import com.bcosmos.worker.persistence.WorkerPersistenceService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code repo.commit.discovered.v1} to analyse individual commit blobs without checkout (ADR 0011, spec v2 section 5.2).
 */
@Component
public class CommitDiscoveredConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommitDiscoveredConsumer.class);

    private final WorkerPersistenceService persistence;
    private final CommitAnalysisPipeline pipeline;
    private final FindingsPublisher publisher;
    private final WorkerProperties properties;
    private final ObjectMapper json;
    private final JavaType envelopeType;

    public CommitDiscoveredConsumer(
            WorkerPersistenceService persistence,
            CommitAnalysisPipeline pipeline,
            FindingsPublisher publisher,
            WorkerProperties properties,
            ObjectMapper json) {
        this.persistence = persistence;
        this.pipeline = pipeline;
        this.publisher = publisher;
        this.properties = properties;
        this.json = json;
        this.envelopeType = json.getTypeFactory().constructParametricType(
                EventEnvelope.class, CommitDiscoveredPayload.class);
    }

    @KafkaListener(topics = KafkaTopics.COMMIT_DISCOVERED, groupId = "${spring.kafka.consumer.group-id:bcosmos-analysis-worker}")
    public void consume(String message) {
        EventEnvelope<CommitDiscoveredPayload> envelope;
        try {
            envelope = json.readValue(message, envelopeType);
        } catch (Exception e) {
            log.error("Failed to deserialize CommitDiscovered message: {}", e.getMessage());
            throw new IllegalArgumentException("Corrupt CommitDiscovered message", e);
        }

        if (!envelope.understandsSchema()) {
            throw new IllegalArgumentException("Unsupported schema version: " + envelope.schemaVersion());
        }

        CommitDiscoveredPayload payload = envelope.payload();
        Long repositoryId = envelope.repositoryId();
        String commitHash = payload.commitHash();
        String parentHash = payload.parentHashes().isEmpty() ? null : payload.parentHashes().get(0);

        // Record the commit discovered in Postgres (idempotent)
        persistence.saveCommitRecord(
                repositoryId,
                commitHash,
                payload.parentHashes(),
                payload.committedAt(),
                payload.changedJavaFiles().size());

        // Resolve git repository
        File repoGitDir = resolveGitDir(repositoryId);
        if (repoGitDir == null || !repoGitDir.exists()) {
            throw new IllegalStateException("Git repository not found for repositoryId " + repositoryId);
        }

        String analyzerVersion = pipeline.analyzerVersion();
        String rulesetHash = pipeline.rulesetHash();

        try (Repository repo = new FileRepositoryBuilder().setGitDir(repoGitDir).readEnvironment().findGitDir().build()) {
            for (String filePath : payload.changedJavaFiles()) {
                // Check idempotency gate for the AFTER side
                if (!persistence.isFileResultProcessed(repositoryId, commitHash, filePath, "AFTER", analyzerVersion, rulesetHash)) {
                    processFileAtCommit(repo, repositoryId, commitHash, parentHash, filePath,
                            FindingsPayload.Side.AFTER, analyzerVersion, rulesetHash, envelope.correlationId());
                } else {
                    log.debug("Skipping already processed file {} at commit {} (AFTER)", filePath, commitHash);
                }

                // If parent commit exists, process BEFORE side if not already done
                if (parentHash != null && !persistence.isFileResultProcessed(repositoryId, parentHash, filePath, "BEFORE", analyzerVersion, rulesetHash)) {
                    try {
                        processFileAtCommit(repo, repositoryId, parentHash, null, filePath,
                                FindingsPayload.Side.BEFORE, analyzerVersion, rulesetHash, envelope.correlationId());
                    } catch (Exception e) {
                        log.debug("Parent commit {} file {} could not be read or was created in this commit: {}",
                                parentHash, filePath, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error reading repository for commit " + commitHash, e);
        }
    }

    private void processFileAtCommit(
            Repository repo,
            Long repositoryId,
            String commitHash,
            String parentHash,
            String filePath,
            FindingsPayload.Side side,
            String analyzerVersion,
            String rulesetHash,
            String correlationId) {

        JGitBlobSource source = new JGitBlobSource(repo, commitHash);
        CommitAnalysisResult result;
        try {
            result = pipeline.analyse(filePath, source);
        } catch (Exception e) {
            log.warn("Failed analysing blob for {} at {}: {}", filePath, commitHash, e.getMessage());
            result = new CommitAnalysisResult(filePath, "", "java", 0, ParseStatus.FAILED, e.getMessage(), List.of(), analyzerVersion, rulesetHash);
        }

        // Idempotent record
        persistence.recordFileResult(
                repositoryId,
                commitHash,
                filePath,
                side.name(),
                analyzerVersion,
                rulesetHash,
                result.parseStatus().name(),
                result.findings().size());

        List<FindingPayload> findingPayloads = result.findings().stream()
                .map(CommitDiscoveredConsumer::toFindingPayload)
                .toList();

        FindingsPayload findingsPayload = new FindingsPayload(
                commitHash,
                parentHash,
                filePath,
                result.contentHash(),
                result.lineCount(),
                side,
                analyzerVersion,
                rulesetHash,
                result.parseStatus().name(),
                findingPayloads);

        EventEnvelope<FindingsPayload> findingsEnvelope = EventEnvelope.of(
                com.bcosmos.core.events.EventType.FINDINGS,
                repositoryId,
                correlationId,
                findingsPayload);

        publisher.publish(findingsEnvelope);
    }

    private static FindingPayload toFindingPayload(Finding f) {
        return new FindingPayload(
                f.ruleId(),
                f.severity().name(),
                f.message(),
                f.span().startLine(),
                f.span().endLine(),
                f.symbolQualifiedName(),
                f.fingerprint(),
                f.snippet());
    }

    private File resolveGitDir(Long repositoryId) {
        Path base = Path.of(properties.cloneDir());
        // Try base/<repositoryId>/.git
        Path repoPath = base.resolve(String.valueOf(repositoryId));
        if (Files.exists(repoPath.resolve(".git"))) {
            return repoPath.resolve(".git").toFile();
        }
        if (Files.exists(repoPath.resolve("HEAD"))) {
            return repoPath.toFile(); // Bare repo
        }
        // Try base/.git
        if (Files.exists(base.resolve(".git"))) {
            return base.resolve(".git").toFile();
        }
        if (Files.exists(base.resolve("HEAD"))) {
            return base.toFile();
        }
        return repoPath.resolve(".git").toFile();
    }
}
