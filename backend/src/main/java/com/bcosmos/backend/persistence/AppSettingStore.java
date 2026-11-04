package com.bcosmos.backend.persistence;

import java.time.Instant;
import java.util.Optional;

/**
 * The narrow data-access port the settings code needs.
 *
 * <p>It exists so a switch can be unit-tested without a database, the same reason {@code LlmModelStore} exists.
 * {@link JpaAppSettingStore} is the only implementation.</p>
 */
public interface AppSettingStore {

    /** The row for one key, or empty when the setting has never been written. */
    Optional<AppSettingRecord> find(String key);

    /** Inserts or replaces the row for one key. */
    AppSettingRecord put(String key, String value, Instant now);

    /** Removes the row for one key; a missing row is not an error. */
    void delete(String key);
}
