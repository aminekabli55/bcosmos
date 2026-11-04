package com.bcosmos.backend.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Shared pagination rules for the REST API (spec v2 section 8: pagination on every list endpoint).
 *
 * <p>Page size is clamped so a single request cannot ask the database for an unbounded result set; page indexes are
 * zero-based, matching Spring Data and the Angular client.</p>
 */
public final class Pagination {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private Pagination() {
    }

    /** Clamps a requested page size into {@code [1, MAX_SIZE]}. */
    public static int clamp(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** A page request with an explicit order. */
    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clamp(size), sort);
    }
}
