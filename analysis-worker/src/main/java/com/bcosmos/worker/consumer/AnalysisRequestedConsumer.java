package com.bcosmos.worker.consumer;

import com.bcosmos.core.events.AnalysisRequestedPayload;
import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.KafkaTopics;
import com.bcosmos.core.model.ParseStatus;
import com.bcosmos.core.pipeline.CommitAnalysisPipeline;
import com.bcosmos.core.pipeline.CommitAnalysisResult;
import com.bcosmos.worker.persistence.WorkerAnalysisRunEntity;
import com.bcosmos.worker.persistence.WorkerPersistenceService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code repo.analysis.requested.v1} to analyse registered local repositories (spec v2 section 5.1).
 */
@Component
public class AnalysisRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestedConsumer.class);

    private static final List<String> IGNORED_DIRS = List.of(
            ".git", "target", "build", "node_modules", ".mvn", ".idea", ".gradle");

    private final WorkerPersistenceService persistence;
    private final CommitAnalysisPipeline pipeline;
    private final ObjectMapper json;
    private final JavaType envelopeType;

    public AnalysisRequestedConsumer(
            WorkerPersistenceService persistence,
            CommitAnalysisPipeline pipeline,
            ObjectMapper json) {
        this.persistence = persistence;
        this.pipeline = pipeline;
        this.json = json;
        this.envelopeType = json.getTypeFactory().constructParametricType(
                EventEnvelope.class, AnalysisRequestedPayload.class);
    }

    @KafkaListener(topics = KafkaTopics.ANALYSIS_REQUESTED, groupId = "${spring.kafka.consumer.group-id:bcosmos-analysis-worker}")
    public void consume(String message) {
        EventEnvelope<AnalysisRequestedPayload> envelope;
        try {
            envelope = json.readValue(message, envelopeType);
        } catch (Exception e) {
            log.error("Failed to deserialize AnalysisRequested message: {}", e.getMessage());
            throw new IllegalArgumentException("Corrupt AnalysisRequested message", e);
        }

        if (!envelope.understandsSchema()) {
            throw new IllegalArgumentException("Unsupported schema version: " + envelope.schemaVersion());
        }

        AnalysisRequestedPayload payload = envelope.payload();
        long runId = payload.analysisRunId();

        Optional<WorkerAnalysisRunEntity> existingRun = persistence.findRun(runId);
        if (existingRun.isPresent() && "SUCCEEDED".equals(existingRun.get().getStatus())) {
            log.info("Analysis run {} already SUCCEEDED, ignoring duplicate event", runId);
            return;
        }

        Path repoPath = Path.of(payload.localPath());
        if (!Files.exists(repoPath) || !Files.isDirectory(repoPath)) {
            persistence.failRun(runId, "PATH_NOT_FOUND", "Repository path does not exist or is not a directory");
            throw new IllegalArgumentException("Repository path not found: " + payload.localPath());
        }

        List<String> discoveredFiles = discoverSupportedFiles(repoPath);
        persistence.startRun(runId, discoveredFiles.size());

        LocalFileSource fileSource = new LocalFileSource(repoPath);
        int parsedCount = 0;
        int failedCount = 0;

        for (String relativePath : discoveredFiles) {
            try {
                CommitAnalysisResult result = pipeline.analyse(relativePath, fileSource);
                if (result.parseStatus() == ParseStatus.OK) {
                    parsedCount++;
                } else {
                    failedCount++;
                }

                persistence.saveFileAnalysis(
                        runId,
                        result.relativePath(),
                        result.languageId(),
                        result.contentHash(),
                        result.lineCount(),
                        result.parseStatus().name(),
                        result.parseError(),
                        result.findings());

            } catch (Exception e) {
                log.warn("Error analysing file {} in run {}: {}", relativePath, runId, e.getMessage());
                failedCount++;
                persistence.saveFileAnalysis(
                        runId,
                        relativePath,
                        payload.primaryLanguage(),
                        null,
                        0,
                        ParseStatus.FAILED.name(),
                        e.getMessage(),
                        List.of());
            }
        }

        persistence.completeRun(
                runId,
                parsedCount,
                failedCount,
                pipeline.analyzerVersion(),
                pipeline.analyzerVersion(),
                pipeline.rulesetHash());

        log.info("Finished analysis run {}: {} parsed, {} failed", runId, parsedCount, failedCount);
    }

    private List<String> discoverSupportedFiles(Path root) {
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(p -> !isIgnored(root, p))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        files.add(rel);
                    });
        } catch (IOException e) {
            log.error("Failed walking directory {}: {}", root, e.getMessage());
        }
        return files;
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".d.ts")) {
            return false;
        }
        return name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx");
    }

    private boolean isIgnored(Path root, Path file) {
        Path rel = root.relativize(file);
        for (Path part : rel) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
