package com.bcosmos.backend.settings;

import java.time.Instant;

/**
 * The state of the AI master switch as the Settings screen shows it.
 *
 * @param enabled         whether the AI layer is on right now
 * @param propertyDefault what the deployment property ({@code bcosmos.ai.enabled}) says, so the screen can offer
 *                        "follow the deployment default" and explain where the current value comes from
 * @param overridden      whether a stored setting is in force; {@code false} means the property decides
 * @param updatedAt       when the stored setting was written, or {@code null} when there is none
 */
public record AiSettings(boolean enabled, boolean propertyDefault, boolean overridden, Instant updatedAt) {
}
