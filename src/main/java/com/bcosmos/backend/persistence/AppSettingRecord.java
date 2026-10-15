package com.bcosmos.backend.persistence;

import java.time.Instant;
import java.util.Objects;

/**
 * One stored setting as data, not as a JPA entity.
 *
 * <p>The store port speaks this record so the code that reads a setting can be unit-tested without a database and so
 * nothing outside {@code persistence} handles a managed entity.</p>
 *
 * @param key       dotted setting name, e.g. {@code ai.enabled}
 * @param value     the stored value as text; callers know how to read their own key
 * @param updatedAt when the row was last written, for the Settings screen
 */
public record AppSettingRecord(String key, String value, Instant updatedAt) {

    public AppSettingRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
