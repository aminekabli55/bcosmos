package com.bcosmos.backend.web;

import com.bcosmos.backend.corpus.CorpusService;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusIngestRequest;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusIngestResponse;
import com.bcosmos.backend.web.dto.CorpusResponses.CorpusStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Phase 4 corpus endpoints (spec v2 section 8: {@code POST /api/corpus/ingest},
 * {@code GET /api/corpus/status}).
 *
 * <p>No absolute path and no clone location appears in either answer: a mined repository is identified by its URL,
 * and where the miner keeps its clone is the miner's business (spec v2 section 10).</p>
 */
@RestController
@RequestMapping("/api/corpus")
public class CorpusController {

    private final CorpusService corpus;

    public CorpusController(CorpusService corpus) {
        this.corpus = corpus;
    }

    /** {@code POST /api/corpus/ingest} — start mining a list of public repository URLs. */
    @PostMapping("/ingest")
    public CorpusIngestResponse ingest(@RequestBody(required = false) CorpusIngestRequest request) {
        return corpus.ingest(request == null ? null : request.urls(),
                request == null ? null : request.commitWindow());
    }

    /** {@code GET /api/corpus/status} — how far the pipeline got, read from Postgres. */
    @GetMapping("/status")
    public CorpusStatusResponse status() {
        return corpus.status();
    }
}
