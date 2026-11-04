package com.bcosmos.backend.ai.rag;

import com.bcosmos.backend.persistence.FileContentEntity;
import com.bcosmos.backend.persistence.FileContentJpaRepository;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.SymbolEntity;
import com.bcosmos.backend.persistence.SymbolJpaRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the symbols of a run together with the source they came from, as {@link SymbolSource}s.
 *
 * <p>This is the only place that touches the database for chunking, and it is read-only: everything downstream
 * works on plain values. A symbol whose file or stored revision is missing is skipped rather than guessed at — a
 * run from before {@code file_content} existed has symbols and no source, and saying so is the honest answer.</p>
 */
@Component
public class RunSources {

    private final SymbolJpaRepository symbols;
    private final FileRecordJpaRepository files;
    private final FileContentJpaRepository contents;

    public RunSources(SymbolJpaRepository symbols, FileRecordJpaRepository files,
            FileContentJpaRepository contents) {
        this.symbols = symbols;
        this.files = files;
        this.contents = contents;
    }

    /** Chunk sources of one run, in file and position order; empty when the run has no source to chunk. */
    @Transactional(readOnly = true)
    public List<SymbolSource> sourcesOf(long runId) {
        List<SymbolEntity> symbolsOfRun = symbols.findByRunIdOrderByFileIdAscStartLineAsc(runId);
        if (symbolsOfRun.isEmpty()) {
            return List.of();
        }
        Map<Long, FileRecordEntity> fileById = files
                .findByRunIdAndIdIn(runId, symbolsOfRun.stream().map(SymbolEntity::getFileId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(FileRecordEntity::getId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
        Map<Long, FileContentEntity> contentByFileId = contents.findAllById(fileById.keySet()).stream()
                .collect(Collectors.toMap(FileContentEntity::getFileId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        return symbolsOfRun.stream()
                .filter(symbol -> fileById.containsKey(symbol.getFileId()))
                .filter(symbol -> contentByFileId.containsKey(symbol.getFileId()))
                .map(symbol -> new SymbolSource(
                        symbol.getId(),
                        symbol.getFileId(),
                        symbol.getKind(),
                        symbol.getQualifiedName(),
                        fileById.get(symbol.getFileId()).getRelativePath(),
                        symbol.getSpan(),
                        contentByFileId.get(symbol.getFileId()).getText()))
                .toList();
    }

    /** How many symbols of a run could not be chunked because their stored source is missing. */
    @Transactional(readOnly = true)
    public long symbolsWithoutSource(long runId) {
        long total = symbols.countByRunId(runId);
        return total - sourcesOf(runId).size();
    }
}
