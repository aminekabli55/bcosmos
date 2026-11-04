package com.bcosmos.backend.ai.gateway;

/**
 * Whether the whole AI layer may send anything anywhere (spec v2 section 10: "the whole AI layer can be turned off;
 * analysis still works").
 *
 * <p>The gateway depends on this one method rather than on {@code AiProperties} so the switch can be stored in the
 * database and edited from the Settings screen. {@code com.bcosmos.backend.settings.AiSwitch} is the implementation:
 * the deployment property is the default, a stored setting overrides it.</p>
 */
@FunctionalInterface
public interface AiEnablement {

    /** {@code false} means no provider is called at all; nothing is sent and nothing is paid for. */
    boolean enabled();
}
