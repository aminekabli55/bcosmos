package com.bcosmos.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.AppSettingRecord;
import com.bcosmos.backend.persistence.AppSettingStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The AI master switch is the gate that keeps source code on the machine, so both halves of its contract are checked
 * here: the deployment property decides until a setting is stored, and the stored setting wins afterwards.
 */
class AiSwitchTest {

    private static final Instant WRITTEN = Instant.parse("2026-10-14T09:00:00Z");

    private final FakeSettingStore store = new FakeSettingStore();
    private final Clock clock = Clock.fixed(WRITTEN, ZoneOffset.UTC);

    @Test
    void followsTheDeploymentPropertyUntilASettingIsStored() {
        AiSwitch on = new AiSwitch(store, properties(true), clock);

        assertThat(on.enabled()).isTrue();
        assertThat(on.read().overridden()).isFalse();
        assertThat(on.read().propertyDefault()).isTrue();
        assertThat(on.read().updatedAt()).isNull();
        assertThat(store.rows).isEmpty();
    }

    @Test
    void aStoredSettingOverridesTheDeploymentProperty() {
        AiSwitch on = new AiSwitch(store, properties(true), clock);

        AiSettings stored = on.write(false);

        assertThat(stored.enabled()).isFalse();
        assertThat(stored.propertyDefault()).isTrue();
        assertThat(stored.overridden()).isTrue();
        assertThat(stored.updatedAt()).isEqualTo(WRITTEN);
        assertThat(on.enabled()).isFalse();
        assertThat(store.rows).containsEntry(AiSwitch.SETTING_KEY, "false");
    }

    @Test
    void theSwitchCanTurnTheLayerOnWhereTheDeploymentDefaultIsOff() {
        AiSwitch off = new AiSwitch(store, properties(false), clock);

        assertThat(off.enabled()).isFalse();
        assertThat(off.write(true).enabled()).isTrue();
        assertThat(off.enabled()).isTrue();
    }

    @Test
    void clearingTheOverrideGoesBackToTheDeploymentProperty() {
        AiSwitch on = new AiSwitch(store, properties(true), clock);
        on.write(false);

        AiSettings cleared = on.write(null);

        assertThat(cleared.overridden()).isFalse();
        assertThat(cleared.updatedAt()).isNull();
        assertThat(cleared.enabled()).isTrue();
        assertThat(on.enabled()).isTrue();
        assertThat(store.rows).isEmpty();
    }

    @Test
    void ownsExactlyOneSettingKey() {
        assertThat(AiSwitch.SETTING_KEY).isEqualTo("ai.enabled");
    }

    private static AiProperties properties(boolean enabled) {
        return new AiProperties(enabled, Duration.ofSeconds(30), Duration.ofSeconds(5), 3, Duration.ofMillis(500), 2.0,
                2048, "");
    }

    /** An in-memory settings table, so the switch is tested without a database. */
    private static final class FakeSettingStore implements AppSettingStore {

        private final Map<String, String> rows = new LinkedHashMap<>();
        private final Map<String, Instant> written = new LinkedHashMap<>();

        @Override
        public Optional<AppSettingRecord> find(String key) {
            return rows.containsKey(key)
                    ? Optional.of(new AppSettingRecord(key, rows.get(key), written.get(key)))
                    : Optional.empty();
        }

        @Override
        public AppSettingRecord put(String key, String value, Instant now) {
            rows.put(key, value);
            written.put(key, now);
            return new AppSettingRecord(key, value, now);
        }

        @Override
        public void delete(String key) {
            rows.remove(key);
            written.remove(key);
        }
    }
}
