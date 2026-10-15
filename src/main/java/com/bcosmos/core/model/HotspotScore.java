package com.bcosmos.core.model;

import java.util.Objects;

/**
 * Hotspot score and formula components for prioritizing quality remediation (Phase 8, spec section 7.7).
 *
 * <p>Hotspot scoring is explicitly an operator triage heuristic, combining finding severity weights,
 * architectural centrality (dependents count / incoming degree), and file size.</p>
 *
 * @param symbolQualifiedName qualified name of the symbol or class
 * @param filePath             relative file path
 * @param lineCount            number of lines spanned
 * @param findingsCount        total findings within this symbol or file
 * @param highSeverityCount    findings with HIGH or CRITICAL severity
 * @param inDegree             incoming dependent count (who depends on this symbol)
 * @param outDegree            outgoing dependency count (what this symbol calls)
 * @param score                calculated composite hotspot score
 */
public record HotspotScore(
        String symbolQualifiedName,
        String filePath,
        int lineCount,
        int findingsCount,
        int highSeverityCount,
        int inDegree,
        int outDegree,
        double score) {

    public HotspotScore {
        Objects.requireNonNull(symbolQualifiedName, "symbolQualifiedName");
        Objects.requireNonNull(filePath, "filePath");
    }

    /**
     * Calculates the transparent triage hotspot score:
     * findings * 10.0 + highSeverity * 20.0 + inDegree * 5.0 + lineCount * 0.05
     */
    public static double calculate(int findingsCount, int highSeverityCount, int inDegree, int lineCount) {
        return (findingsCount * 10.0)
                + (highSeverityCount * 20.0)
                + (inDegree * 5.0)
                + (Math.min(lineCount, 2000) * 0.05);
    }
}
