package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import java.time.Instant;

/**
 * A registered repository as the API shows it.
 *
 * <p>The canonical local path is private metadata (spec v2 sections 4.1 and 8) and is deliberately absent: the
 * caller already knows the folder it registered, and no other response should leak a filesystem layout.</p>
 */
public record RepositoryResponse(
        Long id,
        String name,
        RepositorySourceType sourceType,
        String primaryLanguage,
        RepositoryStatus status,
        Instant createdAt) {

    public static RepositoryResponse of(RepositoryEntity entity) {
        return new RepositoryResponse(entity.getId(), entity.getName(), entity.getSourceType(),
                entity.getPrimaryLanguage(), entity.getStatus(), entity.getCreatedAt());
    }
}
