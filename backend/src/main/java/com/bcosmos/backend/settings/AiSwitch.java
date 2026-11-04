package com.bcosmos.backend.settings;

import com.bcosmos.backend.ai.gateway.AiEnablement;
import com.bcosmos.backend.config.AiProperties;
import com.bcosmos.backend.persistence.AppSettingStore;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The AI master switch, as a stored setting with the deployment property as its default.
 *
 * <p>No row means the property decides ({@code bcosmos.ai.enabled}); a row overrides it. That keeps a deployment
 * opinion possible while letting the Settings screen turn the whole AI layer off, which is what spec v2 section 10
 * asks the UI to offer.</p>
 *
 * <p>The stored value is read on every gateway call instead of being cached, so a switch flipped in the browser
 * takes effect on the next request rather than at the next restart. It costs one indexed primary-key read per model
 * call, which is nothing next to the call itself.</p>
 */
@Service
public class AiSwitch implements AiEnablement {

    /** The one key this class owns; {@code app_setting} is shared with the settings added later. */
    public static final String SETTING_KEY = "ai.enabled";

    private final AppSettingStore settings;
    private final AiProperties properties;
    private final Clock clock;

    public AiSwitch(AppSettingStore settings, AiProperties properties, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean enabled() {
        return load().enabled();
    }

    /** The switch plus where its current value comes from. */
    @Transactional(readOnly = true)
    public AiSettings read() {
        return load();
    }

    /**
     * Sets or clears the stored switch.
     *
     * @param enabled {@code true}/{@code false} to store an override, {@code null} to remove it and follow the
     *                deployment property again
     */
    @Transactional
    public AiSettings write(Boolean enabled) {
        if (enabled == null) {
            settings.delete(SETTING_KEY);
        } else {
            settings.put(SETTING_KEY, Boolean.toString(enabled), clock.instant());
        }
        return load();
    }

    private AiSettings load() {
        return settings.find(SETTING_KEY)
                .map(row -> new AiSettings(Boolean.parseBoolean(row.value()), properties.enabled(), true,
                        row.updatedAt()))
                .orElseGet(() -> new AiSettings(properties.enabled(), properties.enabled(), false, null));
    }
}
