package com.bcosmos.backend.jobs;

import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import com.bcosmos.backend.persistence.TriageStatus;
import com.bcosmos.backend.repository.DiscoveredFile;
import com.bcosmos.backend.repository.SkippedFile;
import com.bcosmos.core.model.Finding;
import com.bcosmos.core.model.ParseStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the result of one file in its own transaction.
 *
 * <p>One transaction per file keeps a long analysis from holding a database transaction open for minutes, and it
 * means the rows of finished files survive a failure later in the run. The run's counters are updated separately by
 * {@link AnalysisRunService}.</p>
 */
@Service
public class AnalysisPersistenceService {

    private final FileRecordJpaRepository fileRecords;
    private final SymbolJpaRepository symbols;
    private final FindingJpaRepository findings;
    private final Clock clock;

    public AnalysisPersistenceService(FileRecordJpaRepository fileRecords, SymbolJpaRepository symbols,
            FindingJpaRepository findings, Clock clock) {
        this.fileRecords = fileRecords;
        this.symbols = symbols;
        this.findings = findings;
        this.clock = clock;
    }

    /** Stores the file record plus its symbols and findings. */
    @Transactional
    public void saveAnalysedFile(long runId, DiscoveredFile file, String languageId, String contentHash,
            FileAnalysis analysis) {
        FileRecordEntity record = fileRecords.save(new FileRecordEntity(runId, file.relativePathString(), languageId,
                contentHash, analysis.lineCount(), analysis.parseStatus(), truncate(analysis.error(), 1000)));

        if (!analysis.symbols().isEmpty()) {
            List<SymbolEntity> entities = analysis.symbols().stream()
                    .map(symbol -> new SymbolEntity(runId, record.getId(), symbol.kind(), symbol.name(),
                            symbol.qualifiedName(), symbol.parentQualifiedName(), symbol.span(),
                            symbol.signatureHash()))
                    .toList();
            symbols.saveAll(entities);
        }

        if (!analysis.findings().isEmpty()) {
            Instant now = clock.instant();
            List<FindingEntity> entities = analysis.findings().stream()
                    .map(finding -> toEntity(runId, record.getId(), finding, now))
                    .toList();
            findings.saveAll(entities);
        }
    }

    /** Records a file discovery refused, so the run can count it and the UI can show why. */
    @Transactional
    public void saveSkippedFile(long runId, SkippedFile skipped, String languageId) {
        fileRecords.save(new FileRecordEntity(runId, skipped.relativePath(), languageId, null, 0,
                ParseStatus.FAILED, truncate(skipped.reason(), 1000)));
    }

    private static FindingEntity toEntity(long runId, long fileId, Finding finding, Instant createdAt) {
        return new FindingEntity(runId, fileId, truncate(finding.tool(), 40), truncate(finding.toolVersion(), 120),
                truncate(finding.ruleId(), 200), truncate(finding.originalSeverity(), 40), finding.severity(),
                truncate(finding.message(), 1000), finding.span(), truncate(finding.symbolQualifiedName(), 1000),
                truncate(finding.snippet(), 600), truncate(finding.normalizedSnippet(), 600),
                finding.fingerprint(), TriageStatus.OPEN, createdAt);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
