package com.bcosmos.backend.web;

import com.bcosmos.backend.settings.AiSwitch;
import com.bcosmos.backend.web.dto.AiSettingsResponse;
import com.bcosmos.backend.web.dto.UpdateAiSettingsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings that are editable from the browser (spec v2 section 9: the Settings screen drives these server-side).
 *
 * <p>Only the AI master switch lives here so far. The model registry keeps its own endpoints ({@code /api/models}),
 * and provider keys are never an API resource: they come from environment variables (spec section 10).</p>
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AiSwitch ai;

    public SettingsController(AiSwitch ai) {
        this.ai = ai;
    }

    /** {@code GET /api/settings/ai} — the switch, and whether a stored value or the property decides it. */
    @GetMapping("/ai")
    public AiSettingsResponse ai() {
        return AiSettingsResponse.of(ai.read());
    }

    /**
     * {@code PUT /api/settings/ai} — store the switch, or clear the override by sending no value.
     *
     * <p>A missing body is treated like a body without {@code enabled}: it clears the override.</p>
     */
    @PutMapping("/ai")
    public AiSettingsResponse updateAi(@RequestBody(required = false) UpdateAiSettingsRequest request) {
        return AiSettingsResponse.of(ai.write(request == null ? null : request.enabled()));
    }
}
