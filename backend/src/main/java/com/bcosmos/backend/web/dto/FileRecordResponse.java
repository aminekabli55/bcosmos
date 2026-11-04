package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.core.model.ParseStatus;

/**
 * One file of a run (spec v2 section 12, Phase 1: "parse failures are counted in the run and never hidden").
 *
 * @param parseError why the file failed, or {@code null} when it parsed
 */
public record FileRecordResponse(
        Long id,
        String filePath,
        String language,
        ParseStatus parseStatus,
        String parseError,
        int lineCount) {

    public static FileRecordResponse of(FileRecordEntity entity) {
        return new FileRecordResponse(entity.getId(), entity.getRelativePath(), entity.getLanguage(),
                entity.getParseStatus(), entity.getParseError(), entity.getLineCount());
    }
}
