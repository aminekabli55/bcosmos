package com.bcosmos.backend.jobs;

import com.bcosmos.backend.persistence.FileContentEntity;
import com.bcosmos.backend.persistence.FileContentJpaRepository;
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
    private final FileContentJpaRepository contents;
    private final SymbolJpaRepository symbols;
    private final FindingJpaRepository findings;
    private final com.bcosmos.backend.persistence.RelationshipJpaRepository relationships;
    private final Clock clock;

    public AnalysisPersistenceService(FileRecordJpaRepository fileRecords, FileContentJpaRepository contents,
            SymbolJpaRepository symbols, FindingJpaRepository findings,
            com.bcosmos.backend.persistence.RelationshipJpaRepository relationships, Clock clock) {
        this.fileRecords = fileRecords;
        this.contents = contents;
        this.symbols = symbols;
        this.findings = findings;
        this.relationships = relationships;
        this.clock = clock;
    }

    /**
     * Stores the file record, its stored source, its symbols and its findings.
     *
     * <p>The source is stored with the run so Phase 3 can chunk a symbol and apply a proposed diff without reading
     * the repository again (the file may have changed since the run). It is written in the same transaction as the
     * file record it belongs to, so a stored revision without a file record cannot exist.</p>
     *
     * @param source the text that was analysed, or {@code null} when the file could not be read at all
     */
    @Transactional
    public void saveAnalysedFile(long runId, DiscoveredFile file, String languageId, String contentHash,
            String source, FileAnalysis analysis) {
        FileRecordEntity record = fileRecords.save(new FileRecordEntity(runId, file.relativePathString(), languageId,
                contentHash, analysis.lineCount(), analysis.parseStatus(), truncate(analysis.error(), 1000)));

        if (source != null && !source.isEmpty()) {
            contents.save(new FileContentEntity(record.getId(), source, clock.instant()));
        }

        List<SymbolEntity> savedSymbols = List.of();
        if (!analysis.symbols().isEmpty()) {
            List<SymbolEntity> entities = analysis.symbols().stream()
                    .map(symbol -> new SymbolEntity(runId, record.getId(), symbol.kind(), symbol.name(),
                            symbol.qualifiedName(), symbol.parentQualifiedName(), symbol.span(),
                            symbol.signatureHash()))
                    .toList();
            savedSymbols = symbols.saveAll(entities);
        }

        if (!analysis.relationships().isEmpty() && !savedSymbols.isEmpty()) {
            java.util.Map<String, Long> symbolIdByQName = new java.util.HashMap<>();
            for (SymbolEntity s : savedSymbols) {
                symbolIdByQName.put(s.getQualifiedName(), s.getId());
            }

            List<com.bcosmos.backend.persistence.RelationshipEntity> relEntities = new java.util.ArrayList<>();
            for (com.bcosmos.core.model.Relationship rel : analysis.relationships()) {
                Long sourceId = symbolIdByQName.get(rel.sourceQualifiedName());
                if (sourceId != null) {
                    Long targetId = rel.targetQualifiedName() != null ? symbolIdByQName.get(rel.targetQualifiedName()) : null;
                    relEntities.add(new com.bcosmos.backend.persistence.RelationshipEntity(
                            runId,
                            sourceId,
                            targetId,
                            rel.targetReferenceName(),
                            rel.type(),
                            targetId != null ? com.bcosmos.core.model.ResolutionStatus.RESOLVED : rel.resolutionStatus(),
                            targetId != null ? 1.0 : rel.confidence(),
                            rel.evidenceFile(),
                            rel.evidenceLine()));
                }
            }
            if (!relEntities.isEmpty()) {
                relationships.saveAll(relEntities);
            }
        }

        if (!analysis.findings().isEmpty()) {
            Instant now = clock.instant();
            List<FindingEntity> entities = analysis.findings().stream()
                    .map(finding -> toEntity(runId, record.getId(), finding, now))
                    .toList();
            findings.saveAll(entities);
        }
    }

    /**
     * Cross-file resolution pass for relationships whose targets were saved in other files of the run.
     */
    @Transactional
    public void resolveRunRelationships(long runId) {
        List<com.bcosmos.backend.persistence.RelationshipEntity> runRels = relationships.findByRunId(runId);
        List<SymbolEntity> runSymbols = symbols.findByRunId(runId);
        if (runRels.isEmpty() || runSymbols.isEmpty()) {
            return;
        }

        java.util.Map<String, Long> symbolIdByQName = new java.util.HashMap<>();
        java.util.Map<String, List<Long>> symbolIdsBySimpleName = new java.util.HashMap<>();
        for (SymbolEntity s : runSymbols) {
            symbolIdByQName.put(s.getQualifiedName(), s.getId());
            symbolIdsBySimpleName.computeIfAbsent(s.getName(), k -> new java.util.ArrayList<>()).add(s.getId());
        }

        for (com.bcosmos.backend.persistence.RelationshipEntity rel : runRels) {
            if (rel.getTargetSymbolId() == null) {
                String targetRef = rel.getTargetReferenceName();
                // Check if target ref matches a qualified name directly
                if (symbolIdByQName.containsKey(targetRef)) {
                    rel.setTargetSymbolId(symbolIdByQName.get(targetRef));
                    rel.setResolutionStatus(com.bcosmos.core.model.ResolutionStatus.RESOLVED);
                    rel.setConfidence(1.0);
                } else {
                    // Strip any receiver prefix: e.g. "repository.save" -> "save"
                    String simple = targetRef.contains(".") ? targetRef.substring(targetRef.lastIndexOf('.') + 1) : targetRef;
                    List<Long> candidates = symbolIdsBySimpleName.getOrDefault(simple, java.util.Collections.emptyList());
                    if (candidates.size() == 1) {
                        rel.setTargetSymbolId(candidates.getFirst());
                        rel.setResolutionStatus(com.bcosmos.core.model.ResolutionStatus.RESOLVED);
                        rel.setConfidence(0.8);
                    } else if (candidates.size() > 1) {
                        rel.setResolutionStatus(com.bcosmos.core.model.ResolutionStatus.AMBIGUOUS);
                        rel.setConfidence(0.5);
                    }
                }
            }
        }
        relationships.saveAll(runRels);
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
