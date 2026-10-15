package com.bcosmos.backend.persistence;

/**
 * The review decision on a suggestion (spec v2 section 8, {@code POST /api/suggestions/{id}/review}).
 *
 * <p>A suggestion starts as {@link #PROPOSED} and only a human moves it. Nothing in BCosmos applies a patch, so
 * {@link #ACCEPTED} means "a reviewer would take this", not "this was applied" (spec v2 section 1.3).</p>
 */
public enum SuggestionStatus {
    PROPOSED,
    ACCEPTED,
    REJECTED,
    DEFERRED
}
