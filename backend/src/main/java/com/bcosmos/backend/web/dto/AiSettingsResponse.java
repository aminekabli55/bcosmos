package com.bcosmos.backend.web.dto;

import com.bcosmos.backend.settings.AiSettings;
import java.time.Instant;

/**
 * The AI master switch as the Settings screen sees it.
 *
 * <p>It says what the switch is set to <em>and</em> where that value comes from, so the screen can label a stored
 * value as an override and a value inherited from the deployment as a default. No credential is involved: keys stay
 * in the environment (spec v2 section 10).</p>
 *
 * @param enabled         whether the AI layer is on right now
 * @param propertyDefault what {@code bcosmos.ai.enabled} says in this deployment
 * @param overridden      whether a stored setting is in force instead of the property
 * @param updatedAt       when the stored setting was written, or {@code null} when there is none
 */
public record AiSettingsResponse(boolean enabled, boolean propertyDefault, boolean overridden, Instant updatedAt) {

    public static AiSettingsResponse of(AiSettings settings) {
        return new AiSettingsResponse(
                settings.enabled(),
                settings.propertyDefault(),
                settings.overridden(),
                settings.updatedAt());
    }
}
