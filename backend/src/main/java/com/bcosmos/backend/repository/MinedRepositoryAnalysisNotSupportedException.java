package com.bcosmos.backend.repository;

/**
 * Thrown when an on-demand analysis is requested for a mined repository (spec v2 section 8, ADR 0024).
 *
 * <p>Mined repositories do not have a canonical local path; their analysis is driven exclusively by
 * commit discovery events ({@code repo.commit.discovered.v1}) via the Kafka pipeline.</p>
 */
public class MinedRepositoryAnalysisNotSupportedException extends RuntimeException {

    public MinedRepositoryAnalysisNotSupportedException(long repositoryId) {
        super("Repository " + repositoryId + " is a MINED repository and cannot be analysed on-demand; "
                + "mined repositories are analysed via commit discovery events");
    }
}
