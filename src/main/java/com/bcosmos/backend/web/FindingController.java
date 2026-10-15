package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.explanation.FindingNotFoundException;
import com.bcosmos.backend.persistence.FileRecordEntity;
import com.bcosmos.backend.persistence.FileRecordJpaRepository;
import com.bcosmos.backend.persistence.FindingEntity;
import com.bcosmos.backend.persistence.FindingJpaRepository;
import com.bcosmos.backend.web.dto.FindingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One finding by id: {@code GET /api/findings/{id}}.
 *
 * <p>An addition to the spec's endpoint table, needed by the AI Review screen: it is reached by link and cannot
 * rely on having the analysed run's findings page in memory. The run-scoped list endpoint stays the way to browse
 * findings.</p>
 */
@RestController
@RequestMapping("/api/findings")
public class FindingController {

    private final FindingJpaRepository findings;
    private final FileRecordJpaRepository files;

    public FindingController(FindingJpaRepository findings, FileRecordJpaRepository files) {
        this.findings = findings;
        this.files = files;
    }

    /** {@code GET /api/findings/{id}} — one finding with its file path and stored snippet. */
    @GetMapping("/{id}")
    public FindingResponse get(@PathVariable long id) {
        FindingEntity finding = findings.findById(id).orElseThrow(() -> new FindingNotFoundException(id));
        String filePath = files.findById(finding.getFileId()).map(FileRecordEntity::getRelativePath).orElse(null);
        return FindingResponse.of(finding, filePath);
    }
}
