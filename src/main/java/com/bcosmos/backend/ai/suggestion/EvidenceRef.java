package com.bcosmos.backend.ai.suggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One citation in a suggestion: a file and a line range, plus why it matters.
 *
 * <p>It is a separate top-level record because it is also what the API verifies: the cited file has to be one of
 * the run's files and the line range has to exist in the stored revision. A citation that fails that check turns
 * the verdict's {@code evidenceOk} to false, which is the whole point of citing evidence (spec v2 section 7.3).</p>
 *
 * @param file      repository-relative path the model was shown; an absolute path is a failed citation
 * @param startLine first line of the citation, or {@code null} when the model cited a whole file
 * @param endLine   last line of the citation, or {@code null}
 * @param why       what this evidence is supposed to support
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceRef(String file, Integer startLine, Integer endLine, String why) {
}
