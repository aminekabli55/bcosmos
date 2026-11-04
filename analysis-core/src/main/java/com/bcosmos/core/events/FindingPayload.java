package com.bcosmos.core.events;

/**
 * One finding as it travels to Spark (spec v2 section 5.2).
 *
 * <p>It carries what the fix-triple extraction needs — the fingerprint (identity across commits), the rule, the
 * severity, the location and the snippet — and nothing the dataset does not use.</p>
 */
public record FindingPayload(
        String ruleId,
        String severity,
        String message,
        int startLine,
        int endLine,
        String symbol,
        String fingerprint,
        String snippet) {

    public FindingPayload {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint is required");
        }
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("invalid line range " + startLine + "-" + endLine);
        }
    }
}
