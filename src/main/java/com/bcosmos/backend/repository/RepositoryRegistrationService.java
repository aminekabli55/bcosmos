package com.bcosmos.backend.repository;

import com.bcosmos.backend.persistence.RepositoryEntity;
import com.bcosmos.backend.persistence.RepositoryJpaRepository;
import com.bcosmos.backend.persistence.RepositorySourceType;
import com.bcosmos.backend.persistence.RepositoryStatus;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers repositories and reads them back (spec v2 section 8, UC1).
 *
 * <p>The path is validated by {@link SafePathValidator} and stored canonically; nothing in a repository is read,
 * built or executed at registration time. Phase 1 knows one language, so the registered repository is recorded as
 * Java: language detection per repository arrives with the second adapter (spec section 3.1, phase 10).</p>
 */
@Service
public class RepositoryRegistrationService {

    private static final String PHASE_ONE_LANGUAGE = "java";

    private final RepositoryJpaRepository repositories;
    private final SafePathValidator pathValidator;
    private final Clock clock;

    public RepositoryRegistrationService(RepositoryJpaRepository repositories, SafePathValidator pathValidator,
            Clock clock) {
        this.repositories = repositories;
        this.pathValidator = pathValidator;
        this.clock = clock;
    }

    @Transactional
    public RepositoryEntity register(String requestedName, String localPath, RepositorySourceType sourceType) {
        Path canonicalPath = pathValidator.validateRepositoryPath(localPath);
        String canonical = canonicalPath.toString();

        repositories.findByCanonicalLocalPath(canonical).ifPresent(existing -> {
            throw new DuplicateRepositoryException(
                    "this repository path is already registered as '" + existing.getName() + "'");
        });

        RepositoryEntity entity = new RepositoryEntity(
                nameFor(requestedName, canonicalPath),
                canonical,
                sourceType,
                PHASE_ONE_LANGUAGE,
                RepositoryStatus.REGISTERED,
                clock.instant());
        return repositories.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<RepositoryEntity> list(Pageable pageable) {
        return repositories.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public RepositoryEntity require(long repositoryId) {
        return repositories.findById(repositoryId).orElseThrow(() -> new RepositoryNotFoundException(repositoryId));
    }

    private static String nameFor(String requestedName, Path canonicalPath) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        Path fileName = canonicalPath.getFileName();
        return fileName == null ? canonicalPath.toString() : fileName.toString();
    }
}
