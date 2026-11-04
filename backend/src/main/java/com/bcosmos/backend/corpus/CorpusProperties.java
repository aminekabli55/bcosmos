package com.bcosmos.backend.corpus;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the corpus may ingest (spec v2 sections 6 and 10: the corpus uses permissively licensed public
 * repositories, and the miner only clones from an allowlisted host).
 *
 * <p>The allowlist is enforced here, at ingest time, before any event exists: a rejected URL never reaches the
 * miner, so a misconfigured or hostile request cannot make it clone anything.</p>
 *
 * @param allowedHosts hosts an ingest request may name; empty means nothing may be ingested
 * @param commitWindow default number of commits to walk back, newest first
 */
@ConfigurationProperties(prefix = "bcosmos.corpus")
public record CorpusProperties(List<String> allowedHosts, int commitWindow) {

    public CorpusProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
        commitWindow = commitWindow < 1 ? 200 : commitWindow;
    }
}
