package com.bcosmos.core.model;

/**
 * Normalized severity scale (spec v2 section 4.3).
 *
 * <p>Order matters: {@code INFO < LOW < MEDIUM < HIGH < CRITICAL}. The original tool severity is always kept
 * alongside it in {@link Finding#originalSeverity()} so a mapping mistake stays visible.</p>
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** True when this severity is at least as severe as {@code other}. */
    public boolean atLeast(Severity other) {
        return ordinal() >= other.ordinal();
    }
}
