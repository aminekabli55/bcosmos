package com.bcosmos.backend.persistence;

/**
 * What a run is doing right now (spec v2 section 4.1, {@code analysis_run.stage}).
 *
 * <p>The UI shows this instead of a fake percentage: discovery, parsing, rule analysis and persistence really are
 * the four things that happen, in that order.</p>
 */
public enum AnalysisStage {
    QUEUED,
    DISCOVERY,
    PARSING,
    ANALYSIS,
    PERSISTENCE,
    COMPLETED
}
