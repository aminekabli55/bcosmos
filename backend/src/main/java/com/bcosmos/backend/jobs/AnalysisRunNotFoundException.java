package com.bcosmos.backend.jobs;

/** No analysis run with that id. */
public class AnalysisRunNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AnalysisRunNotFoundException(long runId) {
        super("no analysis run with id " + runId);
    }
}
