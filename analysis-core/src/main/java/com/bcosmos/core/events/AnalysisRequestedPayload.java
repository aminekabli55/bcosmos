package com.bcosmos.core.events;

/**
 * What the worker needs to run a full analysis of a registered local repository.
 *
 * <p>The local path is private and stays inside the pipeline: it travels on Kafka because the worker needs it to
 * read the files, but it is never returned by the API and never logged (spec v2 section 10).</p>
 *
 * @param analysisRunId the run the backend already created and expects the worker to fill in
 * @param localPath     the canonical local repository path
 * @param primaryLanguage the language the adapter should handle, e.g. {@code java}
 */
public record AnalysisRequestedPayload(long analysisRunId, String localPath, String primaryLanguage) {

    public AnalysisRequestedPayload {
        if (analysisRunId <= 0) {
            throw new IllegalArgumentException("analysisRunId must be positive");
        }
        if (localPath == null || localPath.isBlank()) {
            throw new IllegalArgumentException("localPath is required");
        }
    }
}
