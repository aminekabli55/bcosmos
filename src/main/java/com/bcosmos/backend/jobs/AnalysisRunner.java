package com.bcosmos.backend.jobs;

import com.bcosmos.backend.persistence.AnalysisRunEntity;
import com.bcosmos.backend.persistence.AnalysisStage;
import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.repository.DiscoveredFile;
import com.bcosmos.backend.repository.DiscoveryResult;
import com.bcosmos.backend.repository.FileDiscovery;
import com.bcosmos.backend.repository.RepositoryNotFoundException;
import com.bcosmos.backend.repository.SkippedFile;
import com.bcosmos.core.AnalysisException;
import com.bcosmos.core.language.LanguageAdapter;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseDiagnostic;
import com.bcosmos.core.model.ParsedFile;
import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.quality.FindingNormalizer;
import com.bcosmos.core.quality.Fingerprint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Phase 1 pipeline: discover files, parse them, extract symbols, run the rule analysis, store everything and
 * report the counters. It is deliberately language-agnostic — every language-specific step goes through a
 * {@link LanguageAdapter} (ADR 0012).
 *
 * <p>Failure policy: a file that cannot be read, parsed or analysed is recorded as a failed file with its reason
 * and the run continues; an infrastructure failure (database, configuration) fails the whole run with an error
 * code. Nothing is dropped silently (spec v2 section 12, Phase 1).</p>
 */
@Component
public class AnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);
    private static final int MAX_DIAGNOSTICS_IN_MESSAGE = 3;
    private static final int MAX_MESSAGE_LENGTH = 900;

    private final AnalysisRunService runService;
    private final AnalysisPersistenceService persistence;
    private final LanguageAdapterRegistry adapters;
    private final FileDiscovery fileDiscovery;
    private final RepositoryJpaRepository repositories;
    private final FindingNormalizer normalizer = new FindingNormalizer();

    public AnalysisRunner(AnalysisRunService runService, AnalysisPersistenceService persistence,
            LanguageAdapterRegistry adapters, FileDiscovery fileDiscovery,
            RepositoryJpaRepository repositories) {
        this.runService = runService;
        this.persistence = persistence;
        this.adapters = adapters;
        this.fileDiscovery = fileDiscovery;
        this.repositories = repositories;
    }

    void run(long runId) {
        AnalysisRunEntity run = runService.markRunning(runId);
        try {
            RepositoryEntity repository = repositoryOf(run);
            Path root = Path.of(repository.getCanonicalLocalPath());
            LanguageAdapter<?> primary = adapters.primaryAdapter();
            runService.recordTools(runId, primary);

            DiscoveryResult discovery = fileDiscovery.discover(root);
            runService.recordDiscovery(runId, discovery);

            int parsed = 0;
            int failed = 0;
            for (DiscoveredFile file : discovery.files()) {
                FileAnalysis analysis = analyseFile(file);
                String contentHash = contentHashOf(file);
                persistence.saveAnalysedFile(runId, file, languageOf(file.relativePath()), contentHash, analysis);
                if (analysis.parsed()) {
                    parsed++;
                } else {
                    failed++;
                    log.debug("analysis run {}: file {} failed ({})", runId, file.relativePathString(),
                            analysis.error());
                }
            }

            runService.recordStage(runId, AnalysisStage.PERSISTENCE);
            for (SkippedFile skipped : discovery.skipped()) {
                persistence.saveSkippedFile(runId, skipped, languageOf(Path.of(skipped.relativePath())));
                failed++;
            }

            runService.finish(runId, parsed, failed);
            log.info("analysis run {} finished: {} files discovered, {} parsed, {} failed, repository {}", runId,
                    discovery.discoveredCount(), parsed, failed, repository.getId());
        } catch (IOException | RuntimeException e) {
            String code = e instanceof IOException ? "DISCOVERY_ERROR" : "ANALYSIS_ERROR";
            runService.fail(runId, code, describe(e));
            log.error("analysis run {} failed with {}", runId, code);
        }
    }

    private RepositoryEntity repositoryOf(AnalysisRunEntity run) {
        return repositories.findById(run.getRepositoryId())
                .orElseThrow(() -> new RepositoryNotFoundException(run.getRepositoryId()));
    }

    /** Analyses one file with its adapter, turning every failure into a recorded result instead of an exception. */
    private FileAnalysis analyseFile(DiscoveredFile file) {
        String source;
        try {
            source = Files.readString(file.absolutePath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // A file that is not valid UTF-8 or cannot be read is a recorded failure, not a crashed run.
            return FileAnalysis.failed(0, "could not be read as UTF-8 text: " + e.getClass().getSimpleName());
        }
        Optional<LanguageAdapter<?>> adapter = adapters.forFile(file.relativePath());
        if (adapter.isEmpty()) {
            return FileAnalysis.failed(countLines(source), "no language adapter handles this file");
        }
        return analyseWith(adapter.get(), file.relativePath(), source);
    }

    private <T extends ParsedFile> FileAnalysis analyseWith(LanguageAdapter<T> adapter, Path relativePath,
            String source) {
        try {
            T parsed = adapter.parse(relativePath, source);
            if (!parsed.parsed()) {
                return FileAnalysis.notParsed(parsed.lineCount(), describeDiagnostics(parsed.diagnostics()));
            }
            List<Symbol> symbols = adapter.extractSymbols(parsed);
            List<Finding> findings = normalizer.normalize(adapter.analyze(parsed, adapter.defaultRuleSet()));
            return FileAnalysis.parsed(parsed.lineCount(), symbols, findings);
        } catch (AnalysisException e) {
            return FileAnalysis.failed(countLines(source), e.getMessage());
        } catch (RuntimeException e) {
            return FileAnalysis.failed(countLines(source), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String languageOf(Path relativePath) {
        return adapters.forFile(relativePath).map(LanguageAdapter::languageId).orElse("unknown");
    }

    private static String contentHashOf(DiscoveredFile file) {
        try {
            byte[] bytes = Files.readAllBytes(file.absolutePath());
            return Fingerprint.sha256Hex(bytes);
        } catch (IOException e) {
            return Fingerprint.sha256Hex(file.relativePathString() + ":" + e.getClass().getSimpleName());
        }
    }

    private static int countLines(String source) {
        if (source.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String describeDiagnostics(List<ParseDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "the parser rejected this file";
        }
        StringBuilder message = new StringBuilder("parse failed: ");
        diagnostics.stream().limit(MAX_DIAGNOSTICS_IN_MESSAGE).forEach(diagnostic -> {
            if (!message.isEmpty()) {
                message.append("; ");
            }
            if (diagnostic.hasPosition()) {
                message.append("line ").append(diagnostic.line()).append(": ");
            }
            message.append(diagnostic.message());
        });
        String text = message.toString().replaceAll("\s+", " ").trim();
        return text.length() > MAX_MESSAGE_LENGTH ? text.substring(0, MAX_MESSAGE_LENGTH) : text;
    }

    private static String describe(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
    }
}
